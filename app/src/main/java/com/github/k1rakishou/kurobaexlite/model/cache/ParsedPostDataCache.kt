package com.github.k1rakishou.kurobaexlite.model.cache

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.GuardedBy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.asLog

class ParsedPostDataCache(
  private val appContext: Context,
  private val coroutineScope: CoroutineScope,
  private val globalConstants: GlobalConstants,
  private val postCommentParser: PostCommentParser,
  private val postCommentApplier: PostCommentApplier,
  private val postReplyChainManager: PostReplyChainManager
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val catalogParsedPostDataMap = mutableMapWithCap<PostDescriptor, ParsedPostData>(128)
  @GuardedBy("mutex")
  private val threadParsedPostDataMap = mutableMapWithCap<PostDescriptor, ParsedPostData>(1024)

  @GuardedBy("mutex")
  private val catalogPendingUpdates = mutableSetOf<PostDescriptor>()
  @GuardedBy("mutex")
  private val threadPendingUpdates = mutableSetOf<PostDescriptor>()

  private val updateNotifyDebouncer = DebouncingCoroutineExecutor(coroutineScope)

  private val _postDataUpdatesFlow = MutableSharedFlow<Set<PostDescriptor>>(extraBufferCapacity = Channel.UNLIMITED)
  val postDataUpdatesFlow: SharedFlow<Set<PostDescriptor>>
    get() = _postDataUpdatesFlow.asSharedFlow()

  suspend fun ensurePostDataLoaded(
    isCatalog: Boolean,
    postDescriptor: PostDescriptor,
    func: suspend () -> Unit
  ) {
    val alreadyLoaded = mutex.withLock {
      return@withLock if (isCatalog) {
        catalogParsedPostDataMap.containsKey(postDescriptor)
      } else {
        threadParsedPostDataMap.containsKey(postDescriptor)
      }
    }

    if (alreadyLoaded) {
      func()
      return
    }

    val found = AtomicBoolean(false)
    val processed = AtomicBoolean(false)

    postDataUpdatesFlow
      .takeWhile { !processed.get() }
      .cancellable()
      .collect { updated ->
        if (!updated.contains(postDescriptor)) {
          return@collect
        }

        if (!found.compareAndSet(false, true)) {
          return@collect
        }

        try {
          func()
        } finally {
          processed.set(true)
        }
      }
  }

  suspend fun getOrCalculateParsedPostData(
    chanDescriptor: ChanDescriptor,
    postData: PostData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
    force: Boolean
  ): ParsedPostData {
    val postDescriptor = postData.postDescriptor

    val oldParsedPostData = mutex.withLockNonCancellable {
      when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor]
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor]
      }
    }

    if (!force && oldParsedPostData != null) {
      postData.updateParsedPostData(oldParsedPostData)
      notifyListenersPostDataUpdated(chanDescriptor, postData.postDescriptor)

      return oldParsedPostData
    }

    val newParsedPostData = calculateParsedPostData(
      postData = postData,
      parsedPostDataContext = parsedPostDataContext,
      chanTheme = chanTheme
    )

    mutex.withLockNonCancellable {
      when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor] = newParsedPostData
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor] = newParsedPostData
      }
    }

    postData.updateParsedPostData(newParsedPostData)
    notifyListenersPostDataUpdated(chanDescriptor, postData.postDescriptor)

    return newParsedPostData
  }

  private suspend fun notifyListenersPostDataUpdated(
    chanDescriptor: ChanDescriptor,
    postDescriptor: PostDescriptor
  ) {
    mutex.withLockNonCancellable {
      when (chanDescriptor) {
        is CatalogDescriptor -> catalogPendingUpdates += postDescriptor
        is ThreadDescriptor -> threadPendingUpdates += postDescriptor
      }
    }

    updateNotifyDebouncer.post(timeout = 100L) {
      val updates = mutex.withLockNonCancellable {
        when (chanDescriptor) {
          is CatalogDescriptor -> {
            val updates = catalogPendingUpdates.toSet()
            catalogPendingUpdates.clear()

            return@withLockNonCancellable updates
          }
          is ThreadDescriptor -> {
            val updates = threadPendingUpdates.toSet()
            threadPendingUpdates.clear()

            return@withLockNonCancellable updates
          }
        }
      }

      if (updates.isEmpty()) {
        return@post
      }

      _postDataUpdatesFlow.emit(updates)
    }
  }

  fun formatCatalogToolbarTitle(catalogDescriptor: CatalogDescriptor): String {
    return "${catalogDescriptor.siteKeyActual}/${catalogDescriptor.boardCode}/"
  }

  suspend fun formatThreadToolbarTitle(postDescriptor: PostDescriptor): String? {
    return mutex.withLockNonCancellable {
      val parsedPostData = threadParsedPostDataMap[postDescriptor]
        ?: return@withLockNonCancellable null

      if (parsedPostData.parsedPostSubject.isNotNullNorBlank()) {
        return@withLockNonCancellable parsedPostData.parsedPostSubject
      }

      return@withLockNonCancellable parsedPostData.parsedPostComment.take(64)
    }
  }

  suspend fun calculateParsedPostData(
    postData: PostData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme
  ): ParsedPostData {
    BackgroundUtils.ensureBackgroundThread()

    try {
      val textParts = postCommentParser.parsePostComment(
        postData.postCommentUnparsed,
        postData.postDescriptor
      )

      if (parsedPostDataContext.isParsingThread) {
        processReplyChains(postData.postDescriptor, textParts)
      }

      val processedPostComment = postCommentApplier.applyTextPartsToAnnotatedString(
        chanTheme = chanTheme,
        textParts = textParts,
        parsedPostDataContext = parsedPostDataContext
      )
      val postSubjectParsed = HtmlUnescape.unescape(postData.postSubjectUnparsed)

      return ParsedPostData(
        parsedPostParts = textParts,
        parsedPostComment = processedPostComment.text,
        processedPostComment = processedPostComment,
        parsedPostSubject = postSubjectParsed,
        processedPostSubject = parseAndProcessPostSubject(
          chanTheme = chanTheme,
          postIndex = postData.postIndex,
          postDescriptor = postData.postDescriptor,
          postTimeMs = postData.timeMs,
          postImages = postData.images,
          postSubjectParsed = postSubjectParsed,
          parsedPostDataContext = parsedPostDataContext
        ),
        postFooterText = formatFooterText(postData, parsedPostDataContext),
        parsedPostDataContext = parsedPostDataContext,
      )
    } catch (error: Throwable) {
      val postComment = "Error parsing ${postData.postNo}!\n\nError: ${error.asLog()}"
      val postCommentAnnotated = AnnotatedString(postComment)

      return ParsedPostData(
        parsedPostParts = emptyList(),
        parsedPostComment = postComment,
        processedPostComment = postCommentAnnotated,
        parsedPostSubject = "",
        processedPostSubject = AnnotatedString(""),
        postFooterText = AnnotatedString(""),
        parsedPostDataContext = parsedPostDataContext,
      )
    }
  }

  private suspend fun processReplyChains(
    postDescriptor: PostDescriptor,
    textParts: List<PostCommentParser.TextPart>
  ) {
    val repliesTo = mutableSetOf<PostDescriptor>()

    for (textPart in textParts) {
      for (textPartSpan in textPart.spans) {
        if (textPartSpan !is PostCommentParser.TextPartSpan.Linkable) {
          continue
        }

        when (textPartSpan) {
          is PostCommentParser.TextPartSpan.Linkable.Board,
          is PostCommentParser.TextPartSpan.Linkable.Search,
          is PostCommentParser.TextPartSpan.Linkable.Url -> continue
          is PostCommentParser.TextPartSpan.Linkable.Quote -> {
            if (textPartSpan.crossThread) {
              continue
            }

            repliesTo += textPartSpan.postDescriptor
          }
        }
      }
    }

    if (repliesTo.isNotEmpty()) {
      postReplyChainManager.insert(postDescriptor, repliesTo)
    }
  }

  suspend fun parseAndProcessPostSubject(
    chanTheme: ChanTheme,
    postIndex: Int,
    postDescriptor: PostDescriptor,
    postTimeMs: Long?,
    postImages: List<PostImageData>?,
    postSubjectParsed: String,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString {
    val hasImages = postImages?.isNotNullNorEmpty() ?: false
    val hasSubject = postSubjectParsed.isNotBlank()

    return buildAnnotatedString(capacity = postSubjectParsed.length) {
      if (hasSubject) {
        val subjectAnnotatedString = AnnotatedString(
          text = postSubjectParsed,
          spanStyle = SpanStyle(
            color = chanTheme.postSubjectColorCompose,
          )
        )

        append(subjectAnnotatedString)
        append("\n")
      }

      val postInfoPart = buildString(capacity = 32) {
        if (parsedPostDataContext.isParsingThread) {
          append("#")
          append(postIndex + 1)
          append(TEXT_SEPARATOR)
        }

        append("No. ")
        append(postDescriptor.postNo)
      }

      val postInfoPartAnnotatedString = AnnotatedString(
        text = postInfoPart,
        spanStyle = SpanStyle(
          color = chanTheme.textColorHintCompose,
        )
      )

      append(postInfoPartAnnotatedString)

      if (postTimeMs != null) {
        val relativeTime = buildString {
          val timeString = DateUtils.getRelativeTimeSpanString(
            postTimeMs,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS,
            0
          ).toString()

          append(TEXT_SEPARATOR)
          append(timeString)
        }

        val relativeTimeAnnotatedString = AnnotatedString(
          text = relativeTime,
          spanStyle = SpanStyle(
            color = chanTheme.textColorHintCompose,
          )
        )

        append(relativeTimeAnnotatedString)
      }

      if (hasImages) {
        append("\n")

        val imagesInfoAnnotatedString = buildAnnotatedString(capacity = 64) {
          if (postImages!!.size > 1) {
            val imagesCount = postImages.size
            val totalFileSize = postImages.sumOf { it.fileSize }

            append(imagesCount.toString())
            append(" ")
            append("files")
            append(", ")
            append(totalFileSize.asReadableFileSize())
          } else {
            val postImage = postImages.first()

            append(
              AnnotatedString(
                text = postImage.originalFileName,
                spanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
              )
            )
            append(" ")
            append(postImage.ext.uppercase(Locale.ENGLISH))
            append(" ")
            append(postImage.width.toString())
            append("x")
            append(postImage.height.toString())
            append(" ")
            append(postImage.fileSize.asReadableFileSize())
          }

          addStyle(
            style = SpanStyle(color = chanTheme.textColorHintCompose),
            start = 0,
            end = length
          )
        }

        append(imagesInfoAnnotatedString)
      }
    }
  }

  private suspend fun formatFooterText(
    postData: PostData,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString? {
    val postDescriptor = postData.postDescriptor
    val threadImagesTotal = postData.threadImagesTotal
    val threadRepliesTotal = postData.threadRepliesTotal
    val threadPostersTotal = postData.threadPostersTotal
    val isCatalogMode = parsedPostDataContext.isParsingCatalog
    val hasThreadInfo = threadImagesTotal != null || threadRepliesTotal != null || threadPostersTotal != null

    if (isCatalogMode && hasThreadInfo) {
      val text = buildString(capacity = 32) {
        threadRepliesTotal
          ?.takeIf { repliesCount -> repliesCount > 0 }
          ?.let { repliesCount ->
            val repliesText = appContext.resources.getQuantityString(
              R.plurals.reply_with_number,
              repliesCount,
              repliesCount
            )

            append(repliesText)
          }

        threadImagesTotal
          ?.takeIf { imagesCount -> imagesCount > 0 }
          ?.let { imagesCount ->
            if (isNotEmpty()) {
              append(", ")
            }

            val imagesText = appContext.resources.getQuantityString(
              R.plurals.image_with_number,
              imagesCount,
              imagesCount
            )

            append(imagesText)
          }

        threadPostersTotal
          ?.takeIf { postersCount -> postersCount > 0 }
          ?.let { postersCount ->
            if (isNotEmpty()) {
              append(", ")
            }

            val imagesText = appContext.resources.getQuantityString(
              R.plurals.poster_with_number,
              postersCount,
              postersCount
            )

            append(imagesText)
          }
      }

      return AnnotatedString(text)
    }

    val repliesFrom = postReplyChainManager.getRepliesFrom(postDescriptor)
    if (repliesFrom.isNotEmpty()) {
      val repliesFromCount = repliesFrom.size

      val text = appContext.resources.getQuantityString(
        R.plurals.reply_with_number,
        repliesFromCount,
        repliesFromCount
      )

      return AnnotatedString(text)
    }

    return null
  }

  companion object {
    private const val TEXT_SEPARATOR = " • "
  }

}