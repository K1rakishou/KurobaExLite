package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared

import android.os.SystemClock
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequence
import com.github.k1rakishou.kurobaexlite.helpers.bidirectionalSequenceIndexed
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.PostBindProcessor
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

abstract class PostScreenViewModel(
  private val application: KurobaExLiteApplication,
  protected val savedStateHandle: SavedStateHandle
) : BaseAndroidViewModel(application) {
  protected val postReplyChainManager: PostReplyChainManager by inject(PostReplyChainManager::class.java)
  protected val chanCache: ChanCache by inject(ChanCache::class.java)
  protected val chanThreadManager: ChanThreadManager by inject(ChanThreadManager::class.java)
  protected val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)
  protected val postBindProcessor: PostBindProcessor by inject(PostBindProcessor::class.java)
  protected val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  protected val uiInfoManager: UiInfoManager by inject(UiInfoManager::class.java)
  protected val appSettings: AppSettings by inject(AppSettings::class.java)
  protected val globalConstants: GlobalConstants by inject(GlobalConstants::class.java)
  protected val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)

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

  suspend fun onThreadLoadingStart(threadDescriptor: ThreadDescriptor?, loadOptions: LoadOptions) {
    if (loadOptions.showLoadingIndicator) {
      postScreenState.postsAsyncDataState.value = AsyncData.Loading
    }

    postScreenState.updateChanDescriptor(threadDescriptor)
    postScreenState.onStartLoading()

    if (loadOptions.showLoadingIndicator) {
      postListBuilt = CompletableDeferred()
    } else {
      postListBuilt?.cancel()
      postListBuilt = null
    }

    _postsFullyParsedOnceFlow.emit(false)
    postListTouchingBottom.value = false
  }

  suspend fun onCatalogLoadingStart(catalogDescriptor: CatalogDescriptor?, loadOptions: LoadOptions) {
    if (loadOptions.showLoadingIndicator) {
      postScreenState.postsAsyncDataState.value = AsyncData.Loading
    }

    postScreenState.updateChanDescriptor(catalogDescriptor)
    postScreenState.onStartLoading()

    if (loadOptions.showLoadingIndicator) {
      postListBuilt = CompletableDeferred()
    } else {
      postListBuilt?.complete(Unit)
      postListBuilt = null
    }

    _postsFullyParsedOnceFlow.emit(false)
    postListTouchingBottom.value = false
  }

  suspend fun onThreadLoadingEnd(threadDescriptor: ThreadDescriptor) {
    savedStateHandle.set(ThreadScreenViewModel.PREV_THREAD_DESCRIPTOR, threadDescriptor)

    chanCache.onCatalogOrThreadAccessed(threadDescriptor)
    postScreenState.onEndLoading()
  }

  suspend fun onCatalogLoadingEnd(catalogDescriptor: CatalogDescriptor) {
    savedStateHandle.set(CatalogScreenViewModel.PREV_CATALOG_DESCRIPTOR, catalogDescriptor)

    chanCache.onCatalogOrThreadAccessed(catalogDescriptor)
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

  fun reparsePost(postCellData: PostCellData, parsedPostDataContext: ParsedPostDataContext) {
    viewModelScope.launch(globalConstants.postParserDispatcher) {
      val chanTheme = themeEngine.chanTheme

      val parsedPostData = parsedPostDataCache.calculateParsedPostData(
        postCellData = postCellData,
        chanTheme = chanTheme,
        parsedPostDataContext = parsedPostDataContext
      )

      postScreenState.insertOrUpdate(postCellData.copy(parsedPostData = parsedPostData))
    }
  }

  suspend fun reparsePostSubject(postCellData: PostCellData): AnnotatedString? {
    val parsedPostData = postCellData.parsedPostData
      ?: return null
    val chanTheme = themeEngine.chanTheme

    return withContext(globalConstants.postParserDispatcher) {
      return@withContext parsedPostDataCache.parseAndProcessPostSubject(
        chanTheme = chanTheme,
        postIndex = postCellData.originalPostOrder,
        postDescriptor = postCellData.postDescriptor,
        postTimeMs = postCellData.timeMs,
        postImages = postCellData.images,
        postSubjectParsed = parsedPostData.parsedPostSubject,
        parsedPostDataContext = parsedPostData.parsedPostDataContext
      )
    }
  }

  suspend fun parseInitialBatchOfPosts(
    startPostDescriptor: PostDescriptor?,
    count: Int = 32,
    chanDescriptor: ChanDescriptor,
    postDataList: List<IPostData>,
    isCatalogMode: Boolean,
  ): List<PostCellData> {
    if (postDataList.isEmpty()) {
      return emptyList()
    }

    val chanTheme = themeEngine.chanTheme

    return withContext(globalConstants.postParserDispatcher) {
      val startIndex = postDataList
        .indexOfFirst { postData -> postData.postDescriptor == startPostDescriptor }
        .takeIf { index -> index >= 0 }
        ?: 0

      val indexesOfPostsToParse = postDataList.indices
        .toList()
        .bidirectionalSequence(startPosition = startIndex)
        .take(count)
        .toSet()

      logcat {
        "Parsing posts ${indexesOfPostsToParse.size} out of ${postDataList.size}, " +
          "indexes=${indexesOfPostsToParse.joinToString()}"
      }

      val resultList = mutableListWithCap<PostCellData>(postDataList.size)

      for (index in postDataList.indices) {
        val postData = postDataList[index]

        val parsedPostData = if (index in indexesOfPostsToParse) {
          calculatePostData(
            chanDescriptor = chanDescriptor,
            postData = postData,
            chanTheme = chanTheme,
            parsedPostDataContext = ParsedPostDataContext(
              isParsingCatalog = isCatalogMode
            )
          )
        } else {
          null
        }

        resultList += PostCellData.fromPostData(postData, parsedPostData)
      }

      return@withContext resultList
    }
  }

  suspend fun parsePosts(
    startPostDescriptor: PostDescriptor?,
    chanDescriptor: ChanDescriptor,
    postCellDataList: List<PostData>,
    count: Int,
    isCatalogMode: Boolean,
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(globalConstants.postParserDispatcher) {
      val startIndex = postCellDataList
        .indexOfFirst { postData -> postData.postDescriptor == startPostDescriptor }
        .takeIf { index -> index >= 0 }
        ?: 0

      for (index in startIndex until (startIndex + count)) {
        val postData = postCellDataList.getOrNull(index) ?: break

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
    postDataList: List<IPostData>,
    parsePostsOptions: ParsePostsOptions = ParsePostsOptions(),
    onStartParsingPosts: suspend () -> Unit,
    onPostsParsed: suspend (List<PostCellData>) -> Unit
  ) {
    currentParseJob?.cancel()
    currentParseJob = null

    currentParseJob = viewModelScope.launch(globalConstants.postParserDispatcher) {
      if (postDataList.isEmpty()) {
        withContext(NonCancellable) { onPostsParsed(emptyList()) }
        return@launch
      }

      val chunksCount = globalConstants.coresCount.coerceAtLeast(2)
      val chunkSize = (postDataList.size / chunksCount).coerceAtLeast(chunksCount)
      val chanTheme = themeEngine.chanTheme
      val isParsingCatalog = chanDescriptor is CatalogDescriptor

      val mutex = Mutex()
      val resultMap = mutableMapWithCap<PostDescriptor, PostCellData>(postDataList.size)

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
                  ensureActive()

                  val postData = postDataIndexed.value

                  val parsedPostData = calculatePostData(
                    chanDescriptor = chanDescriptor,
                    postData = postData,
                    chanTheme = chanTheme,
                    parsedPostDataContext = ParsedPostDataContext(
                      isParsingCatalog = isParsingCatalog
                    ),
                    force = parsePostsOptions.forced
                  )

                  mutex.withLock {
                    resultMap[postData.postDescriptor] = PostCellData.fromPostData(
                      postData = postData,
                      parsedPostData = parsedPostData
                    )
                  }
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

          val repliesToPostDataSet = hashSetOf<IPostData>()
          repliesToPostDataSet += chanCache.getManyForDescriptor(chanDescriptor, repliesToPostDescriptorSet)
          repliesToPostDataSet += postReplyChainManager.getManyRepliesTo(postDescriptors)
            .mapNotNull { postDescriptor -> postDataMap[postDescriptor] }

          val repliesToChunkSize = (repliesToPostDataSet.size / chunksCount).coerceAtLeast(chunksCount)

          if (repliesToPostDataSet.isNotEmpty()) {
            logcat(tag = TAG) {
              "parseRemainingPostsAsyncReplies() starting parsing ${repliesToPostDataSet.size} posts... " +
                "(chunksCount=$chunksCount, chunkSize=$repliesToChunkSize)"
            }

            supervisorScope {
              repliesToPostDataSet
                .chunked(repliesToChunkSize)
                .mapIndexed { chunkIndex, postDataListChunk ->
                  return@mapIndexed async(globalConstants.postParserDispatcher) {
                    logcat(tag = TAG) {
                      "parseRemainingPostsAsyncReplies() running chunk ${chunkIndex} with " +
                        "${postDataListChunk.size} elements on thread ${Thread.currentThread().name}"
                    }

                    postDataListChunk.forEach { postData ->
                      ensureActive()

                      val parsedPostData = calculatePostData(
                        chanDescriptor = chanDescriptor,
                        postData = postData,
                        chanTheme = chanTheme,
                        parsedPostDataContext = ParsedPostDataContext(
                          isParsingCatalog = isParsingCatalog
                        ),
                        force = true
                      )

                      mutex.withLock {
                        resultMap[postData.postDescriptor] = PostCellData.fromPostData(
                          postData = postData,
                          parsedPostData = parsedPostData
                        )
                      }
                    }

                    logcat(tag = TAG) { "parseRemainingPostsAsyncReplies() chunk ${chunkIndex} processing finished" }
                  }
                }
                .toList()
                .awaitAll()
            }
          }
        }

        val postCellDataList = postDataList.mapNotNull { postData ->
          resultMap[postData.postDescriptor]
        }

        showPostsLoadingSnackbarJob.cancel()
        withContext(NonCancellable) { onPostsParsed(postCellDataList) }

        val deltaTime = SystemClock.elapsedRealtime() - startTime
        logcat(tag = TAG) { "parseRemainingPostsAsync() parsing ${postDataList.size} posts... done! Took ${deltaTime} ms" }
      } catch (error: Throwable) {
        logcat(tag = TAG) { "parseRemainingPostsAsync() error: ${error.asLog()}" }
      } finally {
        showPostsLoadingSnackbarJob.cancel()
      }
    }
  }

  suspend fun restoreScrollPosition(chanDescriptor: ChanDescriptor) {
    logcat(tag = TAG) { "restoreScrollPosition($chanDescriptor)" }

    withContext(Dispatchers.Main) {
      val lastRememberedPosition = lazyColumnRememberedPositionCache[chanDescriptor]
      if (lastRememberedPosition != null) {
        _scrollRestorationEventFlow.emit(lastRememberedPosition)
        return@withContext
      }

      val posts = (postScreenState.postsAsyncDataState.value as? AsyncData.Data)?.data?.posts
      val lastViewedPostDescriptor = postScreenState.lastViewedPostDescriptorForScrollRestoration.value

      if (posts != null && lastViewedPostDescriptor != null) {
        val index = posts
          .indexOfLast { postDataState -> postDataState.value.postDescriptor == lastViewedPostDescriptor }

        if (index > 0) {
          uiInfoManager.orientations.forEach { orientation ->
            val newLastRememberedPosition = LazyColumnRememberedPosition(
              orientation = orientation,
              index = index,
              offset = 0
            )

            _scrollRestorationEventFlow.emit(newLastRememberedPosition)
          }
        }
      }
    }
  }

  private suspend fun calculatePostData(
    chanDescriptor: ChanDescriptor,
    postData: IPostData,
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
      // TODO(KurobaEx): rewrite this
//      postScreenState.updateSearchQuery(searchQuery)
    }
  }

  fun onPostListBuilt() {
    postListBuilt?.complete(Unit)
  }

  fun onPostBind(postCellData: PostCellData) {
    val descriptor = chanDescriptor
      ?: return

    val catalogMode = descriptor is CatalogDescriptor

    postBindProcessor.onPostBind(
      isCatalogMode = catalogMode,
      postsParsedOnce = postsFullyParsedOnceFlow.value,
      postDescriptor = postCellData.postDescriptor
    )
  }

  fun onPostUnbind(postCellData: PostCellData) {
    val descriptor = chanDescriptor
      ?: return

    val catalogMode = descriptor is CatalogDescriptor

    postBindProcessor.onPostUnbind(
      isCatalogMode = catalogMode,
      postDescriptor = postCellData.postDescriptor
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

  data class LoadOptions(
    val showLoadingIndicator: Boolean = true,
    val forced: Boolean = false,
    val loadFromNetwork: Boolean = true
  )

  companion object {
    private const val TAG = "PostScreenViewModel"
    private val DEFAULT_REMEMBERED_POSITION = LazyColumnRememberedPosition(
      orientation = 0,
      index = 0,
      offset = 0
    )
  }

}