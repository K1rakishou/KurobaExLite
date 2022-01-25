package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequence
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class PostScreenViewModel(
  protected val postCommentParser: PostCommentParser,
  protected val postCommentApplier: PostCommentApplier,
  protected val themeEngine: ThemeEngine
) : BaseViewModel() {
  abstract val postScreenState: PostScreenState
  abstract fun reload()

  suspend fun parseComment(postData: PostData): AnnotatedString {
    val chanTheme = themeEngine.chanTheme

    return withContext(Dispatchers.Default) {
      val parsedPostData = postData.getOrCalculateParsedPostParts {
        val textParts = postCommentParser.parsePostComment(postData)
        val processedPostComment = postCommentApplier.processTextParts(chanTheme, textParts)

        return@getOrCalculateParsedPostParts PostData.ParsedPostData(
          parsedPostParts = textParts,
          processedPostComment = processedPostComment
        )
      }

      return@withContext parsedPostData.processedPostComment
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
              processedPostComment = processedPostComment
            )
          }
        }
    }
  }

  interface PostScreenState {
    fun postDataAsync(): AsyncData<List<PostData>>
  }
}