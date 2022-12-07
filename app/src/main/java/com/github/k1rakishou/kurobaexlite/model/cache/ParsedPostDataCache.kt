package com.github.k1rakishou.kurobaexlite.model.cache

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.GuardedBy
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPart
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPartSpan
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.util.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.util.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.managers.MarkedPostManager
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPost
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPostType
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.originalFileNameForPostCell
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
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
import logcat.logcat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ParsedPostDataCache(
  private val appContext: Context,
  private val coroutineScope: CoroutineScope,
  private val postCommentParser: PostCommentParser,
  private val postCommentApplier: PostCommentApplier,
  private val postReplyChainManager: PostReplyChainManager,
  private val markedPostManager: MarkedPostManager
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

  private val _postDataUpdatesFlow = MutableSharedFlow<Pair<ChanDescriptor, Set<PostDescriptor>>>(extraBufferCapacity = Channel.UNLIMITED)
  val postDataUpdatesFlow: SharedFlow<Pair<ChanDescriptor, Set<PostDescriptor>>>
    get() = _postDataUpdatesFlow.asSharedFlow()

  private val _chanDescriptorPostsUpdatedFlow = MutableSharedFlow<ChanDescriptor>(extraBufferCapacity = Channel.UNLIMITED)
  val chanDescriptorPostsUpdatedFlow: SharedFlow<ChanDescriptor>
    get() = _chanDescriptorPostsUpdatedFlow.asSharedFlow()

  suspend fun doWithPostDataOnceItsLoaded(
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
      .collect { (_, updated) ->
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

  suspend fun delete(chanDescriptor: ChanDescriptor, postDescriptors: Collection<PostDescriptor>) {
    mutex.withLock {
      when (chanDescriptor) {
        is CatalogDescriptor -> {
          postDescriptors.forEach { postDescriptor ->
            catalogParsedPostDataMap.remove(postDescriptor)
            catalogPendingUpdates.remove(postDescriptor)
          }
        }
        is ThreadDescriptor -> {
          postDescriptors.forEach { postDescriptor ->
            threadParsedPostDataMap.remove(postDescriptor)
            threadPendingUpdates.remove(postDescriptor)
          }
        }
      }
    }
  }

  suspend fun getManyParsedPostData(
    chanDescriptor: ChanDescriptor,
    postDescriptors: List<PostDescriptor>
  ): Map<PostDescriptor, ParsedPostData> {
    return mutex.withLock {
      val resultMap = mutableMapWithCap<PostDescriptor, ParsedPostData>(postDescriptors.size)

      val parsedPostDataMap = when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap
        is ThreadDescriptor -> threadParsedPostDataMap
      }

      for (postDescriptor in postDescriptors) {
        resultMap[postDescriptor] = parsedPostDataMap[postDescriptor]
          ?: continue
      }

      return@withLock resultMap
    }
  }

  suspend fun getParsedPostData(
    chanDescriptor: ChanDescriptor,
    postDescriptor: PostDescriptor
  ): ParsedPostData? {
    return mutex.withLock {
      when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor]
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor]
      }
    }
  }

  suspend fun getParsedPostData(
    postDescriptor: PostDescriptor
  ): ParsedPostData? {
    return mutex.withLock {
      val fromThread = threadParsedPostDataMap[postDescriptor]
      if (fromThread != null) {
        return@withLock fromThread
      }

      return@withLock catalogParsedPostDataMap[postDescriptor]
    }
  }

  suspend fun getOrCalculateParsedPostData(
    chanDescriptor: ChanDescriptor,
    postData: IPostData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
    forced: Boolean
  ): ParsedPostData {
    val postDescriptor = postData.postDescriptor

    val oldParsedPostData = mutex.withLockNonCancellable {
      when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor]
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor]
      }
    }

    if (!forced && oldParsedPostData != null) {
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

    notifyListenersPostDataUpdated(chanDescriptor, postData.postDescriptor)
    return newParsedPostData
  }

  suspend fun recalculatePostCellData(
    chanDescriptor: ChanDescriptor,
    postCellData: PostCellData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
  ): ParsedPostData {
    val postDescriptor = postCellData.postDescriptor

    val newParsedPostData = calculateParsedPostData(
      postCellData = postCellData,
      parsedPostDataContext = parsedPostDataContext,
      chanTheme = chanTheme
    )

    mutex.withLockNonCancellable {
      when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor] = newParsedPostData
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor] = newParsedPostData
      }
    }

    notifyListenersPostDataUpdated(chanDescriptor, postDescriptor)
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

    _chanDescriptorPostsUpdatedFlow.emit(chanDescriptor)

    updateNotifyDebouncer.post(timeout = 250L) {
      mutex.withLockNonCancellable {
        val updates = when (chanDescriptor) {
          is CatalogDescriptor -> {
            val updates = catalogPendingUpdates.toSet()
            catalogPendingUpdates.clear()

            updates
          }
          is ThreadDescriptor -> {
            val updates = threadPendingUpdates.toSet()
            threadPendingUpdates.clear()

            updates
          }
        }

        if (updates.isEmpty()) {
          return@withLockNonCancellable
        }

        logcat(TAG) { "notifyListenersPostDataUpdated() chanDescriptor: ${chanDescriptor}, updates: ${updates.size}" }
        _postDataUpdatesFlow.emit(Pair(chanDescriptor, updates))
      }
    }
  }

  suspend fun formatThreadToolbarTitle(postDescriptor: PostDescriptor, maxLength: Int = 64): String? {
    val parsedPostData = getParsedPostData(postDescriptor)
      ?: return null

    if (parsedPostData.parsedPostSubject.isNotNullNorBlank()) {
      return parsedPostData.parsedPostSubject
    }

    return parsedPostData.parsedPostComment.take(maxLength)
  }

  fun formatBookmarkTitle(subject: String?, comment: String?, maxLength: Int = 64): String? {
    if (subject.isNotNullNorBlank()) {
      return HtmlUnescape.unescape(subject)
    }

    return comment?.take(maxLength)
  }

  suspend fun calculateParsedPostData(
    postData: IPostData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
  ): ParsedPostData {
    return calculateParsedPostData(
      originalPostOrder = postData.originalPostOrder,
      postCommentUnparsed = postData.postCommentUnparsed,
      postSubjectUnparsed = postData.postSubjectUnparsed,
      timeMs = postData.timeMs,
      opMark = postData.opMark,
      sage = postData.sage,
      name = postData.name,
      tripcode = postData.tripcode,
      posterId = postData.posterId,
      countryFlag = postData.countryFlag,
      boardFlag = postData.boardFlag,
      threadRepliesTotal = postData.threadRepliesTotal,
      threadImagesTotal = postData.threadImagesTotal,
      threadPostersTotal = postData.threadPostersTotal,
      images = postData.images,
      archived = postData.archived,
      deleted = postData.deleted,
      closed = postData.closed,
      sticky = postData.sticky?.toPostCellDataSticky(),
      postDescriptor = postData.postDescriptor,
      parsedPostDataContext = parsedPostDataContext,
      chanTheme = chanTheme,
    )
  }

  suspend fun calculateParsedPostData(
    postCellData: PostCellData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
  ): ParsedPostData {
    return calculateParsedPostData(
      originalPostOrder = postCellData.originalPostOrder,
      postCommentUnparsed = postCellData.postCommentUnparsed,
      postSubjectUnparsed = postCellData.postSubjectUnparsed,
      timeMs = postCellData.timeMs,
      opMark = postCellData.opMark,
      sage = postCellData.sage,
      name = postCellData.name,
      tripcode = postCellData.tripcode,
      posterId = postCellData.posterId,
      countryFlag = postCellData.countryFlag,
      boardFlag = postCellData.boardFlag,
      threadRepliesTotal = postCellData.threadRepliesTotal,
      threadImagesTotal = postCellData.threadImagesTotal,
      threadPostersTotal = postCellData.threadPostersTotal,
      images = postCellData.images,
      archived = postCellData.archived,
      deleted = postCellData.deleted,
      closed = postCellData.closed,
      sticky = postCellData.sticky,
      postDescriptor = postCellData.postDescriptor,
      parsedPostDataContext = parsedPostDataContext,
      chanTheme = chanTheme,
    )
  }

  private suspend fun calculateParsedPostData(
    originalPostOrder: Int,
    postCommentUnparsed: String,
    postSubjectUnparsed: String,
    timeMs: Long?,
    opMark: Boolean,
    sage: Boolean,
    name: String?,
    tripcode: String?,
    posterId: String?,
    countryFlag: PostIcon?,
    boardFlag: PostIcon?,
    threadRepliesTotal: Int?,
    threadImagesTotal: Int?,
    threadPostersTotal: Int?,
    images: List<IPostImage>?,
    archived: Boolean,
    deleted: Boolean,
    closed: Boolean,
    sticky: PostCellData.Sticky?,
    postDescriptor: PostDescriptor,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
  ): ParsedPostData {
    BackgroundUtils.ensureBackgroundThread()

    try {
      val textParts = postCommentParser.parsePostComment(
        postCommentUnparsed = postCommentUnparsed,
        postDescriptor = postDescriptor
      )

      val repliesTo = if (parsedPostDataContext.isParsingThread) {
        processReplyChains(postDescriptor, textParts)
      } else {
        emptySet()
      }

      val processedPostComment = postCommentApplier.applyTextPartsToAnnotatedString(
        markedPosts = getMarkedPostInfoSetForQuoteSpans(textParts),
        chanTheme = chanTheme,
        textParts = textParts,
        parsedPostDataContext = parsedPostDataContext
      )

      val postSubjectParsed = unescapePostSubject(postSubjectUnparsed)

      val isPostMarkedAsMine = markedPostManager.getMarkedPosts(postDescriptor)
        .any { markedPost -> markedPost.markedPostType == MarkedPostType.MyPost }

      val isReplyToPostMarkedAsMine = kotlin.run {
        return@run markedPostManager.getManyMarkedPosts(repliesTo)
          .takeIf { map -> map.isNotEmpty() }
          ?.any { (_, markedPosts) ->
            markedPosts.takeIf { posts -> posts.isNotEmpty() }
              ?.any { markedPost -> markedPost.markedPostType == MarkedPostType.MyPost }
              ?: false
          }
          ?: false
      }

      return ParsedPostData(
        parsedPostParts = textParts,
        repliesTo = repliesTo,
        parsedPostComment = processedPostComment.text,
        processedPostComment = processedPostComment,
        parsedPostSubject = postSubjectParsed,
        processedPostSubject = parseAndProcessPostSubject(
          chanTheme = chanTheme,
          postIndex = originalPostOrder,
          postDescriptor = postDescriptor,
          postTimeMs = timeMs,
          opMark = opMark,
          sage = sage,
          posterName = name,
          posterTripcode = tripcode,
          posterId = posterId,
          postIcons = arrayOf(countryFlag, boardFlag).filterNotNull(),
          postImages = images,
          postSubjectParsed = postSubjectParsed,
          archived = archived,
          deleted = deleted,
          closed = closed,
          sticky = sticky,
          parsedPostDataContext = parsedPostDataContext
        ),
        postFooterText = formatFooterText(
          postDescriptor = postDescriptor,
          threadRepliesTotal = threadRepliesTotal,
          threadImagesTotal = threadImagesTotal,
          threadPostersTotal = threadPostersTotal,
          parsedPostDataContext = parsedPostDataContext
        ),
        isPostMarkedAsMine = isPostMarkedAsMine,
        isReplyToPostMarkedAsMine = isReplyToPostMarkedAsMine,
        parsedPostDataContext = parsedPostDataContext,
      )
    } catch (error: Throwable) {
      logcatError(TAG) { "Error parsing ${postDescriptor}! Error: ${error.asLog()}" }

      val postComment = "Error parsing ${postDescriptor}!\n\nError: ${error.errorMessageOrClassName(userReadable = true)}"
      val postCommentAnnotated = AnnotatedString(postComment)

      return ParsedPostData(
        parsedPostParts = emptyList(),
        repliesTo = emptySet(),
        parsedPostComment = postComment,
        processedPostComment = postCommentAnnotated,
        parsedPostSubject = "",
        processedPostSubject = AnnotatedString(""),
        postFooterText = AnnotatedString(""),
        isPostMarkedAsMine = false,
        isReplyToPostMarkedAsMine = false,
        parsedPostDataContext = parsedPostDataContext,
      )
    }
  }

  fun unescapePostSubject(postSubjectUnparsed: String) =
    HtmlUnescape.unescape(postSubjectUnparsed)

  private suspend fun getMarkedPostInfoSetForQuoteSpans(
    textParts: List<TextPart>
  ): Map<PostDescriptor, Set<MarkedPost>> {
    val foundQuotes = mutableSetOf<PostDescriptor>()

    for (textPart in textParts) {
      for (span in textPart.spans) {
        if (span is TextPartSpan.Linkable.Quote && !span.crossThread) {
          foundQuotes += span.postDescriptor
        }
      }
    }

    if (foundQuotes.isEmpty()) {
      return emptyMap()
    }

    return markedPostManager.getManyMarkedPosts(foundQuotes)
  }

  private suspend fun processReplyChains(
    postDescriptor: PostDescriptor,
    textParts: List<TextPart>
  ): Set<PostDescriptor> {
    val repliesTo = mutableSetOf<PostDescriptor>()

    for (textPart in textParts) {
      for (textPartSpan in textPart.spans) {
        if (textPartSpan !is TextPartSpan.Linkable) {
          continue
        }

        when (textPartSpan) {
          is TextPartSpan.Linkable.Board,
          is TextPartSpan.Linkable.Search,
          is TextPartSpan.Linkable.Url -> continue
          is TextPartSpan.Linkable.Quote -> {
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

    return repliesTo
  }

  fun parseAndProcessPostSubject(
    chanTheme: ChanTheme,
    postIndex: Int,
    postDescriptor: PostDescriptor,
    postTimeMs: Long?,
    opMark: Boolean,
    sage: Boolean,
    posterName: String?,
    posterTripcode: String?,
    posterId: String?,
    postIcons: List<PostIcon>,
    postImages: List<IPostImage>?,
    postSubjectParsed: String,
    archived: Boolean,
    deleted: Boolean,
    closed: Boolean,
    sticky: PostCellData.Sticky?,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString {
    val compactMode = parsedPostDataContext.postViewMode != PostViewMode.List

    return buildAnnotatedString(capacity = postSubjectParsed.length) {
      if (postSubjectParsed.isNotBlank()) {
        val subjectAnnotatedString = AnnotatedString(
          text = postSubjectParsed,
          spanStyle = SpanStyle(
            color = chanTheme.postSubjectColor,
          )
        )

        append(subjectAnnotatedString)
      }

      val canAppendPostNameSection = !compactMode
        && (posterName.isNotNullNorBlank() || posterTripcode.isNotNullNorBlank() || posterId.isNotNullNorBlank())

      if (canAppendPostNameSection) {
        if (length > 0) {
          append("\n")
        }

        appendNameTripcodeId(
          chanTheme = chanTheme,
          opMark = opMark,
          sage = sage,
          posterName = posterName,
          posterTripcode = posterTripcode,
          posterId = posterId
        )
      }

      if (!compactMode) {
        if (length > 0) {
          append("\n")
        }

        append(
          buildAnnotatedString(capacity = 32) {
            pushStyle(SpanStyle(color = chanTheme.postDetailsColor))

            if (parsedPostDataContext.isParsingThread && postIndex > 0) {
              append("#")
              append(postIndex.toString())
              append(AppConstants.TEXT_SEPARATOR)
            }

            append("No. ")
            append(postDescriptor.postNo.toString())
          }
        )

        if (postTimeMs != null) {
          append(
            buildAnnotatedString(capacity = 32) {
              pushStyle(SpanStyle(color = chanTheme.postDetailsColor))

              val timeString = DateUtils.getRelativeTimeSpanString(
                postTimeMs,
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                0
              ).toString()

              append(AppConstants.TEXT_SEPARATOR)
              append(timeString)
            }
          )
        }

        val imagesCount = postImages?.size ?: 0
        if (imagesCount == 1) {
          append("\n")
          appendImagesInfo(
            chanTheme = chanTheme,
            postImages = postImages
          )
        }
      }

      if (postIcons.isNotEmpty() || archived || deleted || closed || sticky != null) {
        if (length > 0) {
          append("\n")
        }

        appendPostIcons(
          archived = archived,
          deleted = deleted,
          closed = closed,
          sticky = sticky,
          postIcons = postIcons,
          chanTheme = chanTheme
        )
      }
    }
  }

  private fun AnnotatedString.Builder.appendPostIcons(
    archived: Boolean,
    deleted: Boolean,
    closed: Boolean,
    sticky: PostCellData.Sticky?,
    postIcons: List<PostIcon>,
    chanTheme: ChanTheme
  ) {
    append(
      buildAnnotatedString(capacity = 16) {
        if (archived) {
          appendInlineContent(id = PostCellIcon.Archived.id)
        }

        if (deleted) {
          if (length > 0) {
            append("  ")
          }

          appendInlineContent(id = PostCellIcon.Deleted.id)
        }

        if (closed) {
          if (length > 0) {
            append("  ")
          }

          appendInlineContent(id = PostCellIcon.Closed.id)
        }

        if (sticky != null) {
          if (length > 0) {
            append("  ")
          }

          appendInlineContent(id = PostCellIcon.Sticky.id)

          if (sticky.maxCapacity != null) {
            append("  ")
            appendInlineContent(id = PostCellIcon.RollingSticky.id)
          }
        }

        if (postIcons.isNotEmpty()) {
          postIcons.forEach { flag ->
            if (length > 0) {
              append("  ")
            }

            when (flag) {
              is PostIcon.CountryFlag -> {
                appendInlineContent(id = PostCellIcon.CountryFlag.id)

                if (flag.flagName.isNotNullNorBlank()) {
                  append("  ")

                  withStyle(
                    SpanStyle(
                      color = chanTheme.postDetailsColor,
                      fontStyle = FontStyle.Italic
                    )
                  ) {
                    append(flag.flagName)
                  }
                }
              }
              is PostIcon.BoardFlag -> {
                appendInlineContent(id = PostCellIcon.BoardFlag.id)

                if (flag.flagName.isNotNullNorBlank()) {
                  append("  ")

                  withStyle(
                    SpanStyle(
                      color = chanTheme.postDetailsColor,
                      fontStyle = FontStyle.Italic
                    )
                  ) {
                    append(flag.flagName)
                  }
                }
              }
            }
          }
        }
      }
    )
  }

  private fun AnnotatedString.Builder.appendImagesInfo(
    chanTheme: ChanTheme,
    postImages: List<IPostImage>?
  ) {
    val imagesInfoAnnotatedString = buildAnnotatedString(capacity = 64) {
      pushStyle(SpanStyle(color = chanTheme.postDetailsColor))

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

        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
          append(postImage.originalFileNameForPostCell())
        }

        append(" ")
        append(postImage.ext.uppercase(Locale.ENGLISH))
        append(" ")
        append(postImage.width.toString())
        append("x")
        append(postImage.height.toString())
        append(" ")
        append(postImage.fileSize.asReadableFileSize())
      }
    }

    append(imagesInfoAnnotatedString)
  }

  private fun AnnotatedString.Builder.appendNameTripcodeId(
    chanTheme: ChanTheme,
    opMark: Boolean,
    sage: Boolean,
    posterName: String?,
    posterTripcode: String?,
    posterId: String?
  ) {
    append(
      buildAnnotatedString(capacity = 32) {
        pushStyle(SpanStyle(color = chanTheme.postNameColor))

        if (posterName.isNotNullNorBlank()) {
          if (length > 0) {
            append(" ")
          }

          if (opMark) {
            withStyle(SpanStyle(color = chanTheme.accentColor)) {
              append("#OP")
              append(" ")
            }
          }

          append(posterName)

          if (sage) {
            withStyle(SpanStyle(color = chanTheme.accentColor)) {
              append(" ")
              append("SAGE")
            }
          }
        }

        if (posterTripcode.isNotNullNorBlank()) {
          if (length > 0) {
            append(" ")
          }

          append(posterTripcode)
        }

        if (posterId.isNotNullNorBlank()) {
          if (length > 0) {
            append(" ")
          }

          withStyle(SpanStyle(color = calculatePosterIdTextColor(chanTheme, posterId))) {
            append(posterId)
          }
        }
      }
    )
  }

  private fun calculatePosterIdTextColor(
    chanTheme: ChanTheme,
    posterId: String
  ): Color {
    // Stolen from the 4chan extension
    val hash: Int = posterId.hashCode()

    val r = hash shr 24 and 0xff
    val g = hash shr 16 and 0xff
    val b = hash shr 8 and 0xff
    val posterIdTextColor = (0xff shl 24) + (r shl 16) + (g shl 8) + b

    val posterIdColorHSL = ThemeEngine.colorToHsl(posterIdTextColor)

    // Make the posterId text color darker if it's too light and the current theme's back color is
    // also light and vice versa
    if (chanTheme.isBackColorDark && posterIdColorHSL.lightness < 0.5) {
      posterIdColorHSL.lightness = .7f
    } else if (chanTheme.isBackColorLight && posterIdColorHSL.lightness > 0.5) {
      posterIdColorHSL.lightness = .3f
    }

    return Color(ThemeEngine.hslToColor(posterIdColorHSL))
  }

  private suspend fun formatFooterText(
    postDescriptor: PostDescriptor,
    threadRepliesTotal: Int?,
    threadImagesTotal: Int?,
    threadPostersTotal: Int?,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString? {
    val isCatalogMode = parsedPostDataContext.isParsingCatalog
    val compactMode = parsedPostDataContext.postViewMode != PostViewMode.List
    val hasThreadInfo = threadImagesTotal != null || threadRepliesTotal != null || threadPostersTotal != null

    if (isCatalogMode && hasThreadInfo) {
      val text = buildString(capacity = 32) {
        threadRepliesTotal
          ?.takeIf { repliesCount -> repliesCount > 0 }
          ?.let { repliesCount ->
            val stringId = if (compactMode) {
              R.plurals.reply_with_number_compact
            } else {
              R.plurals.reply_with_number
            }

            val repliesText = appContext.resources.getQuantityString(
              stringId,
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

            val stringId = if (compactMode) {
              R.plurals.image_with_number_compact
            } else {
              R.plurals.image_with_number
            }

            val imagesText = appContext.resources.getQuantityString(
              stringId,
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

            val stringId = if (compactMode) {
              R.plurals.poster_with_number_compact
            } else {
              R.plurals.poster_with_number
            }

            val imagesText = appContext.resources.getQuantityString(
              stringId,
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

  enum class PostCellIcon(val id: String) {
    Deleted("id_post_deleted"),
    Closed("id_post_deleted"),
    Archived("id_post_archived"),
    Sticky("id_post_sticky"),
    RollingSticky("id_post_rolling_sticky"),
    CountryFlag("id_post_country_flag"),
    BoardFlag("id_post_board_flag")
  }

  companion object {
    private const val TAG = "ParsedPostDataCache"
  }

}