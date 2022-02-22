package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import android.os.SystemClock
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequence
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequenceIndexed
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfoEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

abstract class PostScreenViewModel(
  private val application: KurobaExLiteApplication,
  protected val globalConstants: GlobalConstants,
  protected val themeEngine: ThemeEngine
) : BaseAndroidViewModel(application) {
  private val postReplyChainManager by inject<PostReplyChainManager>(PostReplyChainManager::class.java)
  private val chanThreadCache by inject<ChanThreadCache>(ChanThreadCache::class.java)
  private val parsedPostDataCache by inject<ParsedPostDataCache>(ParsedPostDataCache::class.java)

  private var currentParseJob: Job? = null
  protected var postListBuilt: CompletableDeferred<Boolean>? = null

  private val _snackbarEventFlow = MutableSharedFlow<SnackbarInfoEvent>(extraBufferCapacity = Channel.UNLIMITED)
  val snackbarEventFlow: SharedFlow<SnackbarInfoEvent>
    get() = _snackbarEventFlow.asSharedFlow()

  protected val _postsFullyParsedOnceFlow = MutableStateFlow(false)
  val postsFullyParsedOnceFlow: StateFlow<Boolean>
    get() = _postsFullyParsedOnceFlow.asStateFlow()

  private val _scrollRestorationEventFlow = MutableSharedFlow<LazyColumnRememberedPosition>(extraBufferCapacity = Channel.UNLIMITED)
  val scrollRestorationEventFlow: SharedFlow<LazyColumnRememberedPosition>
    get() = _scrollRestorationEventFlow.asSharedFlow()

  private val _toolbarScrollEventFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = Channel.UNLIMITED)
  val toolbarScrollEventFlow: SharedFlow<Boolean>
    get() = _toolbarScrollEventFlow.asSharedFlow()

  private val lazyColumnRememberedPositionCache = mutableMapOf<ChanDescriptor, LazyColumnRememberedPosition>()

  abstract val postScreenState: PostScreenState

  val chanDescriptor: ChanDescriptor?
    get() = postScreenState.chanDescriptor

  abstract fun reload()
  abstract fun refresh()

  fun rememberedPosition(chanDescriptor: ChanDescriptor?): LazyColumnRememberedPosition {
    if (chanDescriptor == null) {
      return DEFAULT_REMEMBERED_POSITION
    }

    return lazyColumnRememberedPositionCache[chanDescriptor]
      ?: DEFAULT_REMEMBERED_POSITION
  }

  fun rememberPosition(
    chanDescriptor: ChanDescriptor,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int
  ) {
    val postsFullyParsedOnce = postsFullyParsedOnceFlow.value
    if (!postsFullyParsedOnce) {
      return
    }

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

      val parsedPostData = parsedPostDataCache.calculateParsedPostData(
        postData = postData,
        chanTheme = chanTheme,
        parsedPostDataContext = parsedPostDataContext
      )

      postScreenState.updatePost(postData.copy(parsedPostData = parsedPostData))
    }
  }

  suspend fun parseComment(
    chanDescriptor: ChanDescriptor,
    postData: PostData
  ): ParsedPostData {
    val chanTheme = themeEngine.chanTheme

    return withContext(Dispatchers.Default) {
      return@withContext calculatePostData(
        chanDescriptor = chanDescriptor,
        postData = postData,
        chanTheme = chanTheme,
        parsedPostDataContext = ParsedPostDataContext(
          isParsingCatalog = chanDescriptor is CatalogDescriptor
        )
      )
    }
  }

  suspend fun parsePostsAround(
    startIndex: Int = 0,
    count: Int = 32,
    chanDescriptor: ChanDescriptor,
    postDataList: List<PostData>,
    isCatalogMode: Boolean,
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(Dispatchers.Default) {
      postDataList
        .bidirectionalSequence(startPosition = startIndex)
        .take(count)
        .forEach { postData ->
          calculatePostData(
            chanDescriptor = chanDescriptor,
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
    chanDescriptor: ChanDescriptor,
    postDataList: List<PostData>,
    count: Int,
    isCatalogMode: Boolean,
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(Dispatchers.Default) {
      for (index in startIndex until (startIndex + count)) {
        val postData = postDataList.getOrNull(index) ?: break

        calculatePostData(
          chanDescriptor = chanDescriptor,
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
    chanDescriptor: ChanDescriptor,
    postDataList: List<PostData>,
    parsePostsOptions: ParsePostsOptions = ParsePostsOptions(),
    onStartParsingPosts: suspend () -> Unit,
    onPostsParsed: suspend (List<PostData>) -> Unit
  ) {
    currentParseJob?.cancel()
    currentParseJob = null

    currentParseJob = mainScope.launch(Dispatchers.Default) {
      if (postDataList.isEmpty()) {
        onPostsParsed(postDataList)
        return@launch
      }

      val chunksCount = 1 //globalConstants.coresCount.coerceAtLeast(2)
      val chunkSize = (postDataList.size / chunksCount).coerceAtLeast(chunksCount)
      val chanTheme = themeEngine.chanTheme
      val isParsingCatalog = chanDescriptor is CatalogDescriptor

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
                  "parseRemainingPostsAsyncRegular() running chunk ${chunkIndex} with " +
                    "${postDataListChunk.size} elements on thread ${Thread.currentThread().name}"
                }

                postDataListChunk.forEach { postDataIndexed ->
                  val postData = postDataIndexed.value

                  calculatePostData(
                    chanDescriptor = chanDescriptor,
                    postData = postData,
                    chanTheme = chanTheme,
                    parsedPostDataContext = ParsedPostDataContext(
                      isParsingCatalog = isParsingCatalog
                    ),
                    force = parsePostsOptions.forced
                  )
                }

                logcat { "parseRemainingPostsAsyncRegular() chunk ${chunkIndex} processing finished" }
              }
            }
            .toList()
            .awaitAll()
        }

        if (parsePostsOptions.parseRepliesTo) {
          val postDescriptors = postDataList.map { postData -> postData.postDescriptor }
          val postDataMap = postDataList.associateBy { postData -> postData.postDescriptor }
          val repliesToPostDescriptorSet = postReplyChainManager.getManyRepliesTo(postDescriptors)

          val repliesToPostDataSet = hashSetOf<PostData>()
          repliesToPostDataSet += chanThreadCache.getManyForDescriptor(chanDescriptor, repliesToPostDescriptorSet)
          repliesToPostDataSet += postReplyChainManager.getManyRepliesTo(postDescriptors)
            .mapNotNull { postDescriptor -> postDataMap[postDescriptor] }

          val repliesToChunkSize = (repliesToPostDataSet.size / chunksCount).coerceAtLeast(chunksCount)

          if (repliesToPostDataSet.isNotEmpty()) {
            logcat {
              "parseRemainingPostsAsyncReplies() starting parsing ${repliesToPostDataSet.size} posts... " +
                "(chunksCount=$chunksCount, chunkSize=$repliesToChunkSize)"
            }

            repliesToPostDataSet
              .chunked(repliesToChunkSize)
              .mapIndexed { chunkIndex, postDataListChunk ->
                return@mapIndexed async(Dispatchers.IO) {
                  logcat {
                    "parseRemainingPostsAsyncReplies() running chunk ${chunkIndex} with " +
                      "${postDataListChunk.size} elements on thread ${Thread.currentThread().name}"
                  }

                  postDataListChunk.forEach { postData ->
                    calculatePostData(
                      chanDescriptor = chanDescriptor,
                      postData = postData,
                      chanTheme = chanTheme,
                      parsedPostDataContext = ParsedPostDataContext(
                        isParsingCatalog = isParsingCatalog
                      ),
                      force = true
                    )
                  }

                  logcat { "parseRemainingPostsAsyncReplies() chunk ${chunkIndex} processing finished" }
                }
              }
              .toList()
              .awaitAll()
          }
        }

        val deltaTime = SystemClock.elapsedRealtime() - startTime
        logcat { "parseRemainingPostsAsync() parsing ${postDataList.size} posts... done! Took ${deltaTime} ms" }
      } finally {
        showPostsLoadingSnackbarJob.cancel()
        onPostsParsed(postDataList)
      }
    }
  }

  suspend fun restoreScrollPosition(chanDescriptor: ChanDescriptor) {
    withContext(Dispatchers.Main) {
      val lastRememberedPosition = lazyColumnRememberedPositionCache[chanDescriptor]
      if (lastRememberedPosition != null) {
        _scrollRestorationEventFlow.emit(lastRememberedPosition)
      }
    }
  }

  private suspend fun calculatePostData(
    chanDescriptor: ChanDescriptor,
    postData: PostData,
    chanTheme: ChanTheme,
    parsedPostDataContext: ParsedPostDataContext,
    force: Boolean = false
  ): ParsedPostData {
    return parsedPostDataCache.getOrCalculateParsedPostData(
      chanDescriptor = chanDescriptor,
      postData = postData,
      parsedPostDataContext = parsedPostDataContext,
      chanTheme = chanTheme,
      force = force
    )
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

  fun scrollTop() {
    mainScope.launch {
      _toolbarScrollEventFlow.emit(false)
    }
  }

  fun scrollBottom() {
    mainScope.launch {
      _toolbarScrollEventFlow.emit(true)
    }
  }

  fun updateSearchQuery(searchQuery: String?) {
    mainScope.launch {
      postScreenState.updateSearchQuery(searchQuery)
    }
  }

  fun onPostListBuilt() {
    postListBuilt?.complete(true)
  }

  data class ParsePostsOptions(
    val forced: Boolean = false,
    // If set to true then along with new posts the posts these new posts reply to will be re-parsed
    // as well. This is needed to update the "X replies" PostCell text.
    val parseRepliesTo: Boolean = false
  )

  interface PostScreenState {
    val postsAsyncDataState: MutableStateFlow<AsyncData<AbstractPostsState>>
    val threadCellDataState: MutableStateFlow<ThreadCellData?>

    val chanDescriptor: ChanDescriptor?
      get() = doWithDataState { abstractPostsState -> abstractPostsState.chanDescriptor }
    val displayingPostsCount: Int?
      get() = doWithDataState { abstractPostsState -> abstractPostsState.posts.size }
    val searchQuery: String?
      get() = doWithDataState { abstractPostsState -> abstractPostsState.searchQuery }

    fun updatePost(postData: PostData)
    fun updateSearchQuery(searchQuery: String?)

    private fun <T> doWithDataState(func: (AbstractPostsState) -> T): T? {
      val postAsyncData = postsAsyncDataState.value
      if (postAsyncData is AsyncData.Data) {
        return func(postAsyncData.data)
      }

      return null
    }
  }

  companion object {
    private val DEFAULT_REMEMBERED_POSITION = LazyColumnRememberedPosition(0, 0)
  }

}