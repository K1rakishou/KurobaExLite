package com.github.k1rakishou.kurobaexlite.model.source

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.asLog
import java.util.*

class ParsedPostDataCache(
  private val appContext: Context,
  private val globalConstants: GlobalConstants,
  private val postCommentParser: PostCommentParser,
  private val postCommentApplier: PostCommentApplier,
  private val postReplyChainManager: PostReplyChainManager
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val catalogParsedPostDataMap = mutableMapWithCap<PostDescriptor, ParsedPostData>(1024)
  @GuardedBy("mutex")
  private val threadParsedPostDataMap = mutableMapWithCap<PostDescriptor, ParsedPostData>(1024)

  suspend fun getOrCalculateParsedPostData(
    chanDescriptor: ChanDescriptor,
    postData: PostData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme,
    force: Boolean
  ): ParsedPostData {
    return mutex.withLock {
      val postDescriptor = postData.postDescriptor

      val oldParsedPostData = when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor]
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor]
      }

      if (!force && oldParsedPostData != null) {
        return@withLock oldParsedPostData
      }

      val newParsedPostData = calculateParsedPostData(
        postData = postData,
        parsedPostDataContext = parsedPostDataContext,
        chanTheme = chanTheme
      )

      when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor] = newParsedPostData
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor] = newParsedPostData
      }

      postData.updateParsedPostData(newParsedPostData)

      return@withLock newParsedPostData
    }
  }

  suspend fun formatToolbarTitle(
    chanDescriptor: ChanDescriptor,
    postDescriptor: PostDescriptor,
    catalogMode: Boolean
  ): String? {
    if (catalogMode) {
      return "${postDescriptor.siteKeyActual}/${postDescriptor.boardCode}/"
    }

    return mutex.withLock {
      val parsedPostData = when (chanDescriptor) {
        is CatalogDescriptor -> catalogParsedPostDataMap[postDescriptor]
        is ThreadDescriptor -> threadParsedPostDataMap[postDescriptor]
      } ?: return@withLock null

      if (parsedPostData.parsedPostSubject.isNotNullNorBlank()) {
        return@withLock parsedPostData.parsedPostSubject
      }

      return@withLock parsedPostData.parsedPostComment.take(64)
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
        processedPostSubject = parseAndProcessPostSubject(chanTheme, postData, postSubjectParsed),
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
          is PostCommentParser.TextPartSpan.Linkable.Search -> continue
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

  private fun parseAndProcessPostSubject(
    chanTheme: ChanTheme,
    postData: PostData,
    postSubjectParsed: String
  ): AnnotatedString {
    return buildAnnotatedString(capacity = postSubjectParsed.length) {
      if (postSubjectParsed.isNotEmpty()) {
        val subjectAnnotatedString = AnnotatedString(
          text = postSubjectParsed,
          spanStyle = SpanStyle(
            color = chanTheme.postSubjectColorCompose,
          )
        )

        append(subjectAnnotatedString)
        append("\n")
      }

      val postInfoPart = String.format(Locale.ENGLISH, "No. ${postData.postNo}")
      val postInfoPartAnnotatedString = AnnotatedString(
        text = postInfoPart,
        spanStyle = SpanStyle(
          color = chanTheme.textColorHintCompose,
        )
      )

      append(postInfoPartAnnotatedString)
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

    if (isCatalogMode && (threadImagesTotal != null || threadRepliesTotal != null || threadPostersTotal != null)) {
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

}