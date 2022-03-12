package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequence
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequenceIndexed
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.PostBindProcessor
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

abstract class PostScreenViewModel(
  private val application: KurobaExLiteApplication,
  protected val globalConstants: GlobalConstants,
  protected val themeEngine: ThemeEngine,
  protected val savedStateHandle: SavedStateHandle
) : BaseAndroidViewModel(application) {
  private val postReplyChainManager by inject<PostReplyChainManager>(PostReplyChainManager::class.java)
  private val chanThreadCache by inject<ChanThreadCache>(ChanThreadCache::class.java)
  private val chanThreadManager by inject<ChanThreadManager>(ChanThreadManager::class.java)
  private val parsedPostDataCache by inject<ParsedPostDataCache>(ParsedPostDataCache::class.java)
  private val postBindProcessor by inject<PostBindProcessor>(PostBindProcessor::class.java)
  protected val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)

  private var currentParseJob: Job? = null
  protected var postListBuilt: CompletableDeferred<Unit>? = null

  protected val _postsFullyParsedOnceFlow = MutableStateFlow(false)
  val postsFullyParsedOnceFlow: StateFlow<Boolean>
    get() = _postsFullyParsedOnceFlow.asStateFlow()

  private val _scrollRestorationEventFlow = MutableSharedFlow<LazyColumnRememberedPosition>(extraBufferCapacity = Channel.UNLIMITED)
  val scrollRestorationEventFlow: SharedFlow<LazyColumnRememberedPosition>
    get() = _scrollRestorationEventFlow.asSharedFlow()

  private val _toolbarScrollEventFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = Channel.UNLIMITED)
  val toolbarScrollEventFlow: SharedFlow<Boolean>
    get() = _toolbarScrollEventFlow.asSharedFlow()

  val postListTouchingBottom = MutableStateFlow(false)

  val currentlyOpenedCatalogFlow: StateFlow<CatalogDescriptor?>
    get() = chanThreadManager.currentlyOpenedCatalogFlow
  val currentlyOpenedThreadFlow: StateFlow<ThreadDescriptor?>
    get() = chanThreadManager.currentlyOpenedThreadFlow

  private val lazyColumnRememberedPositionCache = mutableMapOf<ChanDescriptor, LazyColumnRememberedPosition>()

  abstract val postScreenState: PostScreenState

  val chanDescriptor: ChanDescriptor?
    get() = postScreenState.chanDescriptor

  abstract fun reload(
    loadOptions: LoadOptions = LoadOptions(),
    onReloadFinished: (() -> Unit)? = null
  )
  
  abstract fun refresh()

  fun onLoadingThread(threadDescriptor: ThreadDescriptor?, loadOptions: LoadOptions) {
    postScreenState.updateChanDescriptor(threadDescriptor)
    postScreenState.onStartLoading()

    if (loadOptions.showLoadingIndicator) {
      postListBuilt = CompletableDeferred()
    } else {
      postListBuilt?.cancel()
      postListBuilt = null
    }

    postListTouchingBottom.value = false
  }

  fun onLoadingCatalog(catalogDescriptor: CatalogDescriptor?, loadOptions: LoadOptions) {
    postScreenState.updateChanDescriptor(catalogDescriptor)
    postScreenState.onStartLoading()

    if (loadOptions.showLoadingIndicator) {
      postListBuilt = CompletableDeferred()
    } else {
      postListBuilt?.complete(Unit)
      postListBuilt = null
    }

    postListTouchingBottom.value = false
  }

  fun onThreadLoaded(threadDescriptor: ThreadDescriptor) {
    postScreenState.onEndLoading()
  }

  fun onCatalogLoaded(catalogDescriptor: CatalogDescriptor) {
    postScreenState.onEndLoading()
  }

  fun rememberedPosition(chanDescriptor: ChanDescriptor, orientation: Int): LazyColumnRememberedPosition {
    val rememberedPosition = lazyColumnRememberedPositionCache[chanDescriptor]
      ?: DEFAULT_REMEMBERED_POSITION

    val finalRememberedPosition = if (rememberedPosition.orientation != orientation) {
      rememberedPosition.copy(offset = 0)
    } else {
      rememberedPosition
    }

    return finalRememberedPosition
  }

  fun rememberPosition(
    chanDescriptor: ChanDescriptor,
    orientation: Int,
    index: Int,
    offset: Int
  ) {
    val postsFullyParsedOnce = postsFullyParsedOnceFlow.value
    if (!postsFullyParsedOnce) {
      return
    }

    val rememberedPosition = LazyColumnRememberedPosition(
      index = index,
      offset = offset,
      orientation = orientation
    )

    lazyColumnRememberedPositionCache[chanDescriptor] = rememberedPosition
  }

  fun resetPosition(chanDescriptor: ChanDescriptor) {
    lazyColumnRememberedPositionCache.remove(chanDescriptor)
  }

  fun reparsePost(postData: PostData, parsedPostDataContext: ParsedPostDataContext) {
    viewModelScope.launch(globalConstants.postParserDispatcher) {
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

    return withContext(globalConstants.postParserDispatcher) {
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

    withContext(globalConstants.postParserDispatcher) {
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

    withContext(globalConstants.postParserDispatcher) {
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

    currentParseJob = viewModelScope.launch(globalConstants.postParserDispatcher) {
      if (postDataList.isEmpty()) {
        withContext(NonCancellable) { onPostsParsed(postDataList) }
        return@launch
      }

      val chunksCount = globalConstants.coresCount.coerceAtLeast(2)
      val chunkSize = (postDataList.size / chunksCount).coerceAtLeast(chunksCount)
      val chanTheme = themeEngine.chanTheme
      val isParsingCatalog = chanDescriptor is CatalogDescriptor

      val showPostsLoadingSnackbarJob = launch {
        delay(125L)
        onStartParsingPosts()
      }

      try {
        val startTime = SystemClock.elapsedRealtime()
        logcat(tag = TAG) {
          "parseRemainingPostsAsync() starting parsing ${postDataList.size} posts... " +
            "(chunksCount=$chunksCount, chunkSize=$chunkSize)"
        }

        supervisorScope {
          postDataList
            .bidirectionalSequenceIndexed(startPosition = 0)
            .chunked(chunkSize)
            .mapIndexed { chunkIndex, postDataListChunk ->
              return@mapIndexed async(globalConstants.postParserDispatcher) {
                logcat(tag = TAG) {
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

                logcat(tag = TAG) { "parseRemainingPostsAsyncRegular() chunk ${chunkIndex} processing finished" }
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
            logcat(tag = TAG) {
              "parseRemainingPostsAsyncReplies() starting parsing ${repliesToPostDataSet.size} posts... " +
                "(chunksCount=$chunksCount, chunkSize=$repliesToChunkSize)"
            }

            repliesToPostDataSet
              .chunked(repliesToChunkSize)
              .mapIndexed { chunkIndex, postDataListChunk ->
                return@mapIndexed async(globalConstants.postParserDispatcher) {
                  logcat(tag = TAG) {
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

                  logcat(tag = TAG) { "parseRemainingPostsAsyncReplies() chunk ${chunkIndex} processing finished" }
                }
              }
              .toList()
              .awaitAll()
          }
        }

        showPostsLoadingSnackbarJob.cancel()
        withContext(NonCancellable) { onPostsParsed(postDataList) }

        val deltaTime = SystemClock.elapsedRealtime() - startTime
        logcat(tag = TAG) { "parseRemainingPostsAsync() parsing ${postDataList.size} posts... done! Took ${deltaTime} ms" }
      } catch (error: Throwable) {
        logcat(tag = TAG) { "parseRemainingPostsAsync() error: ${error.errorMessageOrClassName()}" }
      } finally {
        showPostsLoadingSnackbarJob.cancel()
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

  fun scrollTop() {
    viewModelScope.launch {
      _toolbarScrollEventFlow.emit(false)
    }
  }

  fun scrollBottom() {
    viewModelScope.launch {
      _toolbarScrollEventFlow.emit(true)
    }
  }

  fun updateSearchQuery(searchQuery: String?) {
    viewModelScope.launch {
      postScreenState.updateSearchQuery(searchQuery)
    }
  }

  fun onPostListBuilt() {
    postListBuilt?.complete(Unit)
  }

  fun onPostBind(postData: PostData) {
    val descriptor = chanDescriptor
      ?: return

    val catalogMode = descriptor is CatalogDescriptor

    postBindProcessor.onPostBind(
      isCatalogMode = catalogMode,
      postsParsedOnce = postsFullyParsedOnceFlow.value,
      postDescriptor = postData.postDescriptor
    )
  }

  fun onPostUnbind(postData: PostData) {
    val descriptor = chanDescriptor
      ?: return

    val catalogMode = descriptor is CatalogDescriptor

    postBindProcessor.onPostUnbind(
      isCatalogMode = catalogMode,
      postDescriptor = postData.postDescriptor
    )
  }

  fun toast(message: String) {
    snackbarManager.toast(message)
  }

  open fun resetTimer() {

  }

  data class ParsePostsOptions(
    val forced: Boolean = false,
    // If set to true then along with new posts the posts these new posts reply to will be re-parsed
    // as well. This is needed to update the "X replies" PostCell text.
    val parseRepliesTo: Boolean = false
  )

  abstract class PostScreenState {
    abstract val postsAsyncDataState: MutableStateFlow<AsyncData<AbstractPostsState>>
    abstract val threadCellDataState: MutableStateFlow<ThreadCellData?>
    abstract val lastViewedPostDescriptor: MutableStateFlow<PostDescriptor?>
    abstract val searchQueryFlow: MutableStateFlow<String?>

    private val _chanDescriptorFlow = MutableStateFlow<ChanDescriptor?>(null)
    val chanDescriptorFlow: StateFlow<ChanDescriptor?>
      get() = _chanDescriptorFlow.asStateFlow()
    val chanDescriptor: ChanDescriptor?
      get() = _chanDescriptorFlow.value

    val displayingPostsCount: Int?
      get() = doWithDataState { abstractPostsState -> abstractPostsState.posts.size }

    private val _contentLoaded = MutableStateFlow(false)
    val contentLoaded: StateFlow<Boolean>
      get() = _contentLoaded.asStateFlow()

    abstract fun updatePost(postData: PostData)
    abstract fun updatePosts(postDataCollection: Collection<PostData>)
    abstract fun updateSearchQuery(searchQuery: String?)

    fun updateChanDescriptor(chanDescriptor: ChanDescriptor?) {
      _chanDescriptorFlow.value = chanDescriptor
    }

    fun onStartLoading() {
      _contentLoaded.value = false
    }

    fun onEndLoading() {
      _contentLoaded.value = true
    }

    private fun <T> doWithDataState(func: (AbstractPostsState) -> T): T? {
      val postAsyncData = postsAsyncDataState.value
      if (postAsyncData is AsyncData.Data) {
        return func(postAsyncData.data)
      }

      return null
    }
  }
  
  data class LoadOptions(
    val showLoadingIndicator: Boolean = true,
    val forced: Boolean = false
  )

  companion object {
    private const val TAG = "PostScreenViewModel"
    private val DEFAULT_REMEMBERED_POSITION = LazyColumnRememberedPosition(0, 0)
  }

}