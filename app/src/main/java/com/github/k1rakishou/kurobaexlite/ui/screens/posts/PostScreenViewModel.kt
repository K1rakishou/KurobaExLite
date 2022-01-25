package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequence
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

abstract class PostScreenViewModel(
  protected val postCommentParser: PostCommentParser,
  protected val postCommentApplier: PostCommentApplier,
  protected val themeEngine: ThemeEngine
) : BaseViewModel() {
  abstract val postScreenState: PostScreenState
  abstract fun reload()

  suspend fun parseComment(postData: PostData): PostData.ParsedPostData {
    val chanTheme = themeEngine.chanTheme

    return withContext(Dispatchers.Default) {
      val parsedPostData = postData.getOrCalculateParsedPostParts {
        val textParts = postCommentParser.parsePostComment(postData)
        val processedPostComment = postCommentApplier.processTextParts(chanTheme, textParts)

        return@getOrCalculateParsedPostParts PostData.ParsedPostData(
          parsedPostParts = textParts,
          processedPostComment = processedPostComment,
          processedPostSubject = parseAndProcessPostSubject(chanTheme, postData)
        )
      }

      return@withContext parsedPostData
    }
  }

  suspend fun parsePostsAround(
    startIndex: Int = 0,
    postDataList: List<PostData>,
    count: Int = Int.MAX_VALUE
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(Dispatchers.Default) {
      postDataList
        .bidirectionalSequence(startPosition = startIndex)
        .take(count)
        .forEach { postData ->
          postData.getOrCalculateParsedPostParts {
            val textParts = postCommentParser.parsePostComment(postData)
            val processedPostComment = postCommentApplier.processTextParts(chanTheme, textParts)

            return@getOrCalculateParsedPostParts PostData.ParsedPostData(
              parsedPostParts = textParts,
              processedPostComment = processedPostComment,
              processedPostSubject = parseAndProcessPostSubject(chanTheme, postData)
            )
          }
        }
    }
  }

  private fun parseAndProcessPostSubject(
    chanTheme: ChanTheme,
    postData: PostData
  ): AnnotatedString {
    val postSubjectUnescaped = HtmlUnescape.unescape(postData.postSubjectUnparsed)

    return buildAnnotatedString {
      if (postSubjectUnescaped.isNotEmpty()) {
        val subjectAnnotatedString = AnnotatedString(
          text = postSubjectUnescaped,
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
          color = chanTheme.textColorSecondaryCompose,
        )
      )

      append(postInfoPartAnnotatedString)
    }
  }

  interface PostScreenState {
    fun postDataAsync(): AsyncData<List<PostData>>
  }

  companion object {
    private val EMPTY_ANNOTATED_STRING = AnnotatedString("")
  }
}