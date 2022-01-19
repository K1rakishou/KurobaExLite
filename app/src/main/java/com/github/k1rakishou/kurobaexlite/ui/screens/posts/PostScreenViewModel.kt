package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme

abstract class PostScreenViewModel(
  protected val postCommentParser: PostCommentParser,
  protected val postCommentApplier: PostCommentApplier
) : BaseViewModel() {
  abstract val postScreenState: PostScreenState

  abstract fun reload()

  suspend fun parseComment(chanTheme: ChanTheme, postData: PostData): AnnotatedString {
    val parsedPostData = postData.getOrCalculateParsedPostParts {
      val textParts = postCommentParser.parsePostComment(postData)
      val processedPostComment = postCommentApplier.processTextParts(chanTheme, textParts)

      return@getOrCalculateParsedPostParts PostData.ParsedPostData(
        parsedPostParts = textParts,
        processedPostComment = processedPostComment
      )
    }

    return parsedPostData.processedPostComment
  }

  interface PostScreenState {
    fun postDataAsync(): AsyncData<List<PostData>>
  }
}