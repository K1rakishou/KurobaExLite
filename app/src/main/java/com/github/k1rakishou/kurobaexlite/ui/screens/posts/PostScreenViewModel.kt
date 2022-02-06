package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import android.os.SystemClock
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfoEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import logcat.asLog
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.util.*

abstract class PostScreenViewModel(
  private val application: KurobaExLiteApplication,
  protected val globalConstants: GlobalConstants,
  protected val postCommentParser: PostCommentParser,
  protected val postCommentApplier: PostCommentApplier,
  protected val themeEngine: ThemeEngine
) : BaseAndroidViewModel(application) {
  private val postReplyChainManager by inject<PostReplyChainManager>(PostReplyChainManager::class.java)
  private val scope = CoroutineScope(Dispatchers.Default)

  private var currentParseJob: Job? = null

  private val _snackbarEventFlow = MutableSharedFlow<SnackbarInfoEvent>(extraBufferCapacity = Channel.UNLIMITED)
  val snackbarEventFlow: SharedFlow<SnackbarInfoEvent>
    get() = _snackbarEventFlow.asSharedFlow()

  protected val _postsFullyParsedOnceFlow = MutableStateFlow(false)
  val postsFullyParsedOnceFlow: StateFlow<Boolean>
    get() = _postsFullyParsedOnceFlow.asStateFlow()

  private val _scrollRestorationEventFlow = MutableSharedFlow<LazyColumnRememberedPosition>(extraBufferCapacity = Channel.UNLIMITED)
  val scrollRestorationEventFlow: SharedFlow<LazyColumnRememberedPosition>
    get() = _scrollRestorationEventFlow.asSharedFlow()

  private val lazyColumnRememberedPositionCache = mutableMapOf<ChanDescriptor, LazyColumnRememberedPosition>()

  abstract val postScreenState: PostScreenState
  val chanDescriptor: ChanDescriptor?
    get() = postScreenState.chanDescriptor

  abstract fun reload()
  abstract fun refresh()

  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return postReplyChainManager.getRepliesFrom(postDescriptor)
  }

  fun rememberedPosition(chanDescriptor: ChanDescriptor?): LazyColumnRememberedPosition {
    if (chanDescriptor == null) {
      return DEFAULT_REMEMBERED_POSITION
    }

    return lazyColumnRememberedPositionCache[chanDescriptor]
      ?: DEFAULT_REMEMBERED_POSITION
  }

  fun rememberPosition(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int
  ) {
    val postsFullyParsedOnce = postsFullyParsedOnceFlow.value
    if (!postsFullyParsedOnce) {
      return
    }

    val chanDescriptor = postScreenState.chanDescriptor
      ?: return

    val lazyColumnRememberedPosition = LazyColumnRememberedPosition(
      firstVisibleItemIndex = firstVisibleItemIndex,
      firstVisibleItemScrollOffset = firstVisibleItemScrollOffset
    )

    lazyColumnRememberedPositionCache[chanDescriptor] = lazyColumnRememberedPosition
  }

  fun resetPosition(chanDescriptor: ChanDescriptor) {
    lazyColumnRememberedPositionCache.remove(chanDescriptor)
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

  suspend fun parsePosts(
    startIndex: Int = 0,
    postDataList: List<PostData>,
    count: Int,
    isCatalogMode: Boolean,
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(Dispatchers.Default) {
      for (index in startIndex until (startIndex + count)) {
        val postData = postDataList.getOrNull(index) ?: break

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
    onStartParsingPosts: suspend () -> Unit,
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
        onStartParsingPosts()
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
        showPostsLoadingSnackbarJob.cancel()
        onPostsParsed(postDataList)
      }
    }
  }

  suspend fun restoreScrollPosition(chanDescriptor: ChanDescriptor) {
    withContext(Dispatchers.Main) {
      val lastRememberedPosition = lazyColumnRememberedPositionCache[chanDescriptor]
        ?: return@withContext

      _scrollRestorationEventFlow.emit(lastRememberedPosition)
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
        postData = postData,
        parsedPostDataContext = parsedPostDataContext,
        chanTheme = chanTheme
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

    val repliesFrom = getRepliesFrom(postDescriptor)
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

  protected suspend fun pushCatalogOrThreadPostsLoadingSnackbar(postsCount: Int) {
    pushSnackbar(
      SnackbarInfo(
        id = SnackbarId.CatalogOrThreadPostsLoading,
        aliveUntil = null,
        content = listOf(
          SnackbarContentItem.LoadingIndicator,
          SnackbarContentItem.Spacer(8.dp),
          SnackbarContentItem.Text("Processing ${postsCount} posts…")
        )
      )
    )
  }

  protected suspend fun popCatalogOrThreadPostsLoadingSnackbar() {
    popSnackbar(SnackbarId.CatalogOrThreadPostsLoading)
  }

  protected suspend fun pushSnackbar(snackbarInfo: SnackbarInfo) {
    _snackbarEventFlow.emit(SnackbarInfoEvent.Push(snackbarInfo))
  }

  protected suspend fun popSnackbar(id: SnackbarId) {
    _snackbarEventFlow.emit(SnackbarInfoEvent.Pop(id))
  }

  interface PostScreenState {
    val postsAsyncDataState: MutableStateFlow<AsyncData<IPostsState>>
    val chanDescriptorState: MutableStateFlow<ChanDescriptor?>
    val threadCellDataState: MutableStateFlow<ThreadCellData?>

    val chanDescriptor: ChanDescriptor?
      get() {
        val postAsyncData = postsAsyncDataState.value

        if (postAsyncData is AsyncData.Data) {
          return postAsyncData.data.chanDescriptor
        }

        return null
      }

    fun updatePost(postData: PostData)
  }

  companion object {
    private val DEFAULT_REMEMBERED_POSITION = LazyColumnRememberedPosition(0, 0)
  }

}