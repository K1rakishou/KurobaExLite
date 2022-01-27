package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import kotlinx.coroutines.*
import logcat.asLog
import logcat.logcat
import java.util.*

abstract class PostScreenViewModel(
  protected val globalConstants: GlobalConstants,
  protected val postCommentParser: PostCommentParser,
  protected val postCommentApplier: PostCommentApplier,
  protected val themeEngine: ThemeEngine
) : BaseViewModel() {
  private val scope = CoroutineScope(Dispatchers.Default)
  private var currentParseJob: Job? = null

  abstract val postScreenState: PostScreenState
  abstract fun reload()

  private var _parsingPostsAsync = mutableStateOf(false)
  val parsingPostsAsync: State<Boolean>
    get() = _parsingPostsAsync

  suspend fun parseComment(postData: PostData): PostData.ParsedPostData {
    val chanTheme = themeEngine.chanTheme
    return withContext(Dispatchers.Default) { calculatePostData(postData, chanTheme, true) }
  }

  suspend fun parsePostsAround(
    startIndex: Int = 0,
    postDataList: List<PostData>,
    count: Int
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(Dispatchers.Default) {
      postDataList
        .bidirectionalSequence(startPosition = startIndex)
        .take(count)
        .forEach { postData -> calculatePostData(postData, chanTheme, false) }
    }
  }

  fun parseRemainingPostsAsync(
    postDataList: List<PostData>,
  ) {
    currentParseJob?.cancel()
    currentParseJob = null

    if (postDataList.isEmpty()) {
      return
    }

    val chunkSize = (postDataList.size / globalConstants.coresCount.coerceAtLeast(2))
      .coerceAtLeast(postDataList.size)
    val chanTheme = themeEngine.chanTheme

    currentParseJob = scope.launch(Dispatchers.Default) {
      val showPostsLoadingSnackbarJob = launch {
        delay(125L)
        _parsingPostsAsync.value = true
      }

      try {
        val startTime = SystemClock.elapsedRealtime()
        logcat { "parseRemainingPostsAsync() starting parsing ${postDataList.size} posts... (chunkSize=$chunkSize)" }

        supervisorScope {
          val results = postDataList
            .bidirectionalSequenceIndexed(startPosition = 0)
            .chunked(chunkSize)
            .mapIndexed { chunkIndex, postDataListChunk ->
              return@mapIndexed async(Dispatchers.IO) {
                postDataListChunk.forEachIndexed { originalPostIndexInChunk, postDataIndexed ->
                  val postData = postDataIndexed.value
                  val originalPostIndex = postDataIndexed.index

                  if (LOG_EACH_POST_PARSE) {
                    logcat {
                      "parseRemainingPostsAsync() " +
                        "thread=${Thread.currentThread().name}, " +
                        "chunkIndex=$chunkIndex, " +
                        "originalPostIndexInChunk=$originalPostIndexInChunk, " +
                        "originalPostIndex=$originalPostIndex, " +
                        "postNo=${postData.postNo}"
                    }
                  }

                  calculatePostData(postData, chanTheme, false)
                }
              }
            }

          results.forEach { deferred ->
            try {
              deferred.await()
            } catch (error: Throwable) {
              logcatError { "deferred.await() threw ${error.asLog()}" }
            }
          }
        }

        val deltaTime = SystemClock.elapsedRealtime() - startTime
        logcat { "parseRemainingPostsAsync() starting parsing ${postDataList.size} posts... done! Took ${deltaTime} ms" }
      } finally {
        showPostsLoadingSnackbarJob.cancel()
        _parsingPostsAsync.value = false
      }
    }
  }

  private fun parseAndProcessPostSubject(
    chanTheme: ChanTheme,
    postData: PostData,
    postSubjectParsed: String
  ): AnnotatedString {
    return buildAnnotatedString {
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

  private suspend fun calculatePostData(
    postData: PostData,
    chanTheme: ChanTheme,
    isParsingOnBind: Boolean
  ): PostData.ParsedPostData {
    return postData.getOrCalculateParsedPostParts {
      if (isParsingOnBind) {
        logcat { "calculatePostData() parsing ${postData.postNo}" }
      }

      val textParts = postCommentParser.parsePostComment(postData)
      val processedPostComment = postCommentApplier.processTextParts(chanTheme, textParts)
      val postSubjectParsed = HtmlUnescape.unescape(postData.postSubjectUnparsed)

      return@getOrCalculateParsedPostParts PostData.ParsedPostData(
        parsedPostParts = textParts,
        parsedPostComment = processedPostComment.text,
        processedPostComment = processedPostComment,
        parsedPostSubject = postSubjectParsed,
        processedPostSubject = parseAndProcessPostSubject(chanTheme, postData, postSubjectParsed)
      )
    }
  }

  interface PostScreenState {
    fun postDataAsync(): AsyncData<List<PostData>>
  }

  companion object {
    private val EMPTY_ANNOTATED_STRING = AnnotatedString("")

    private const val LOG_EACH_POST_PARSE = false
  }
}