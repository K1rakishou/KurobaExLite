package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import kotlinx.coroutines.*
import logcat.asLog
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.util.*

abstract class PostScreenViewModel(
  protected val globalConstants: GlobalConstants,
  protected val postCommentParser: PostCommentParser,
  protected val postCommentApplier: PostCommentApplier,
  protected val themeEngine: ThemeEngine
) : BaseViewModel() {
  private val postReplyChainManager by inject<PostReplyChainManager>(PostReplyChainManager::class.java)

  private val scope = CoroutineScope(Dispatchers.Default)
  private var currentParseJob: Job? = null

  abstract val postScreenState: PostScreenState
  abstract fun reload()

  private var _parsingPostsAsyncState = mutableStateOf(false)
  val parsingPostsAsync: State<Boolean>
    get() = _parsingPostsAsyncState

  protected var _chanDescriptorState = mutableStateOf<ChanDescriptor?>(null)
  val chanDescriptorState: State<ChanDescriptor?>
    get() = _chanDescriptorState

  protected var _threadCellDataState = mutableStateOf<ThreadCellData?>(null)
  val threadCellDataState: State<ThreadCellData?>
    get() = _threadCellDataState

  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return postReplyChainManager.getRepliesFrom(postDescriptor)
  }

  fun reparsePost(postData: PostData, parsedPostDataContext: ParsedPostDataContext) {
    viewModelScope.launch(Dispatchers.Default) {
      val chanTheme = themeEngine.chanTheme

      val parsedPostData = calculateParsedPostData(
        postData = postData,
        chanTheme = chanTheme,
        parsedPostDataContext = parsedPostDataContext
      )

      postScreenState.updatePost(postData.copy(parsedPostData = parsedPostData))
    }
  }

  suspend fun parseComment(
    isCatalogMode: Boolean,
    postData: PostData
  ): ParsedPostData {
    val chanTheme = themeEngine.chanTheme

    return withContext(Dispatchers.Default) {
      return@withContext calculatePostData(
        postData = postData,
        chanTheme = chanTheme,
        parsedPostDataContext = ParsedPostDataContext(
          isParsingCatalog = isCatalogMode
        )
      )
    }
  }

  suspend fun parsePostsAround(
    startIndex: Int = 0,
    postDataList: List<PostData>,
    count: Int,
    isCatalogMode: Boolean,
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(Dispatchers.Default) {
      postDataList
        .bidirectionalSequence(startPosition = startIndex)
        .take(count)
        .forEach { postData ->
          calculatePostData(
            postData = postData,
            chanTheme = chanTheme,
            parsedPostDataContext = ParsedPostDataContext(
              isParsingCatalog = isCatalogMode
            )
          )
        }
    }
  }

  fun parseRemainingPostsAsync(
    isCatalogMode: Boolean,
    postDataList: List<PostData>,
    onPostsParsed: suspend (List<PostData>) -> Unit
  ) {
    currentParseJob?.cancel()
    currentParseJob = null

    currentParseJob = scope.launch(Dispatchers.Default) {
      if (postDataList.isEmpty()) {
        onPostsParsed(postDataList)
        return@launch
      }

      val chunksCount = globalConstants.coresCount.coerceAtLeast(2)
      val chunkSize = (postDataList.size / chunksCount).coerceAtLeast(chunksCount)
      val chanTheme = themeEngine.chanTheme

      val showPostsLoadingSnackbarJob = launch {
        delay(125L)
        _parsingPostsAsyncState.value = true
      }

      try {
        val startTime = SystemClock.elapsedRealtime()
        logcat {
          "parseRemainingPostsAsync() starting parsing ${postDataList.size} posts... " +
            "(chunksCount=$chunksCount, chunkSize=$chunkSize)"
        }

        supervisorScope {
          postDataList
            .bidirectionalSequenceIndexed(startPosition = 0)
            .chunked(chunkSize)
            .mapIndexed { chunkIndex, postDataListChunk ->
              return@mapIndexed async(Dispatchers.IO) {
                logcat {
                  "parseRemainingPostsAsync() running chunk ${chunkIndex} with " +
                    "${postDataListChunk.size} elements on thread ${Thread.currentThread().name}"
                }

                postDataListChunk.forEach { postDataIndexed ->
                  val postData = postDataIndexed.value

                  calculatePostData(
                    postData = postData,
                    chanTheme = chanTheme,
                    parsedPostDataContext = ParsedPostDataContext(
                      isParsingCatalog = isCatalogMode
                    )
                  )
                }

                logcat { "parseRemainingPostsAsync() chunk ${chunkIndex} processing finished" }
              }
            }
            .toList()
            .awaitAll()
        }

        val deltaTime = SystemClock.elapsedRealtime() - startTime
        logcat { "parseRemainingPostsAsync() starting parsing ${postDataList.size} posts... done! Took ${deltaTime} ms" }
      } finally {
        onPostsParsed(postDataList)
        showPostsLoadingSnackbarJob.cancel()
        _parsingPostsAsyncState.value = false
      }
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

  private suspend fun calculatePostData(
    postData: PostData,
    chanTheme: ChanTheme,
    parsedPostDataContext: ParsedPostDataContext,
    force: Boolean = false
  ): ParsedPostData {
    return postData.getOrCalculateParsedPostParts(force = force) {
      return@getOrCalculateParsedPostParts calculateParsedPostData(
        postData,
        parsedPostDataContext,
        chanTheme
      )
    }
  }

  private suspend fun calculateParsedPostData(
    postData: PostData,
    parsedPostDataContext: ParsedPostDataContext,
    chanTheme: ChanTheme
  ): ParsedPostData {
    BackgroundUtils.ensureBackgroundThread()

    try {
      val textParts = postCommentParser.parsePostComment(postData)

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
        parsedPostDataContext = parsedPostDataContext
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

  protected open suspend fun postProcessPostDataAfterParsing(postDataList: List<PostData>) {

  }

  interface PostScreenState {
    fun postDataAsyncState(): AsyncData<List<State<PostData>>>
    fun updatePost(postData: PostData)
  }

  data class ThreadCellData(
    val totalReplies: Int = 0,
    val totalImages: Int = 0,
    val totalPosters: Int = 0
  )

}