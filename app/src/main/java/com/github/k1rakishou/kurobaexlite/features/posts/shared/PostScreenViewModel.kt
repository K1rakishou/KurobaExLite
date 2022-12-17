package com.github.k1rakishou.kurobaexlite.features.posts.shared

import android.os.SystemClock
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.bidirectionalSequence
import com.github.k1rakishou.kurobaexlite.helpers.util.bidirectionalSequenceIndexed
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.processDataCollectionConcurrently
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.UpdateBookmarkInfoUponThreadOpen
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.LoadMarkedPosts
import com.github.k1rakishou.kurobaexlite.interactors.navigation.ModifyNavigationHistory
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.FastScrollerMarksManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.PostBindProcessor
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPositionEvent
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

abstract class PostScreenViewModel(
  protected val savedStateHandle: SavedStateHandle
) : BaseViewModel() {
  protected val postReplyChainManager: PostReplyChainManager by inject(PostReplyChainManager::class.java)
  protected val chanCache: ChanCache by inject(ChanCache::class.java)
  protected val chanThreadManager: ChanThreadManager by inject(ChanThreadManager::class.java)
  protected val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)
  protected val postBindProcessor: PostBindProcessor by inject(PostBindProcessor::class.java)
  protected val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  protected val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  protected val appSettings: AppSettings by inject(AppSettings::class.java)
  protected val globalConstants: GlobalConstants by inject(GlobalConstants::class.java)
  protected val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  protected val mediaViewerPostListScroller: MediaViewerPostListScroller by inject(MediaViewerPostListScroller::class.java)
  protected val modifyNavigationHistory: ModifyNavigationHistory by inject(ModifyNavigationHistory::class.java)
  protected val loadMarkedPosts: LoadMarkedPosts by inject(LoadMarkedPosts::class.java)
  protected val loadChanCatalog: LoadChanCatalog by inject(LoadChanCatalog::class.java)
  protected val updateBookmarkInfoUponThreadOpen: UpdateBookmarkInfoUponThreadOpen by inject(UpdateBookmarkInfoUponThreadOpen::class.java)
  protected val fastScrollerMarksManager: FastScrollerMarksManager by inject(FastScrollerMarksManager::class.java)

  private var currentParseJob: Job? = null
  private var updatePostsParsedOnceJob: Job? = null
  private var reparseCatalogPostsWithNewViewModeJob: Job? = null

  protected val _postsFullyParsedOnceFlow = MutableStateFlow(false)
  val postsFullyParsedOnceFlow: StateFlow<Boolean>
    get() = _postsFullyParsedOnceFlow.asStateFlow()

  private val _scrollRestorationEventFlow = MutableSharedFlow<LazyColumnRememberedPositionEvent>(extraBufferCapacity = Channel.UNLIMITED)
  open val scrollRestorationEventFlow: SharedFlow<LazyColumnRememberedPositionEvent>
    get() = _scrollRestorationEventFlow.asSharedFlow()

  private val _postListScrollEventFlow = MutableSharedFlow<ToolbarScrollEvent>(extraBufferCapacity = Channel.UNLIMITED)
  val postListScrollEventFlow: SharedFlow<ToolbarScrollEvent>
    get() = _postListScrollEventFlow.asSharedFlow()

  val mediaViewerScrollEvents: SharedFlow<MediaViewerPostListScroller.ScrollInfo>
    get() = mediaViewerPostListScroller.scrollEventFlow

  val currentlyOpenedCatalogFlow: StateFlow<CatalogDescriptor?>
    get() = chanThreadManager.currentlyOpenedCatalogFlow
  val currentlyOpenedThreadFlow: StateFlow<ThreadDescriptor?>
    get() = chanThreadManager.currentlyOpenedThreadFlow

  private val _fastScrollerMarksFlow = MutableStateFlow<FastScrollerMarksManager.FastScrollerMarks?>(null)
  val fastScrollerMarksFlow: StateFlow<FastScrollerMarksManager.FastScrollerMarks?>
    get() = _fastScrollerMarksFlow.asStateFlow()

  private val lazyColumnRememberedPositionCache = mutableMapOf<ChanDescriptor, LazyColumnRememberedPosition>()

  abstract val postScreenState: PostScreenState

  val chanDescriptor: ChanDescriptor?
    get() = postScreenState.chanDescriptor

  init {
    viewModelScope.launch {
      combine(
        flow = fastScrollerMarksManager.marksUpdatedEventFlow,
        flow2 = chanThreadManager.currentlyOpenedCatalogFlow,
        flow3 = chanThreadManager.currentlyOpenedThreadFlow,
        transform = { t1, t2, t3 -> Triple(t1, t2, t3) }
      )
        .collect { (marksChanDescriptor, currentCatalogDescriptor, currentThreadDescriptor) ->
          val descriptorsMatch = when (marksChanDescriptor) {
            is CatalogDescriptor -> {
              marksChanDescriptor == currentCatalogDescriptor &&
                chanDescriptor == marksChanDescriptor
            }
            is ThreadDescriptor -> {
              marksChanDescriptor == currentThreadDescriptor &&
                chanDescriptor == marksChanDescriptor
            }
          }

          if (!descriptorsMatch) {
            return@collect
          }

          val fastScrollerMarks = fastScrollerMarksManager.getFastScrollerMarks(marksChanDescriptor)
          _fastScrollerMarksFlow.emit(fastScrollerMarks)
        }
    }
  }

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
    postScreenState.onStartLoading(threadDescriptor)

    if (threadDescriptor != null) {
      loadChanCatalog.await(threadDescriptor)
      loadMarkedPosts.load(threadDescriptor)
    }

    updatePostsParsedOnceJob?.cancel()
    updatePostsParsedOnceJob = null

    _postsFullyParsedOnceFlow.emit(false)
  }

  suspend fun onCatalogLoadingStart(catalogDescriptor: CatalogDescriptor?, loadOptions: LoadOptions) {
    if (loadOptions.showLoadingIndicator) {
      postScreenState.postsAsyncDataState.value = AsyncData.Loading
    }

    postScreenState.updateChanDescriptor(catalogDescriptor)
    postScreenState.onStartLoading(catalogDescriptor)

    if (catalogDescriptor != null) {
      loadChanCatalog.await(catalogDescriptor)
    }

    updatePostsParsedOnceJob?.cancel()
    updatePostsParsedOnceJob = null

    _postsFullyParsedOnceFlow.emit(false)
  }

  suspend fun onThreadLoadingEnd(threadDescriptor: ThreadDescriptor?) {
    if (threadDescriptor != null) {
      savedStateHandle.set(LAST_VISITED_THREAD_KEY, threadDescriptor)
    } else {
      savedStateHandle.remove(LAST_VISITED_THREAD_KEY)
    }

    if (threadDescriptor != null) {
      chanCache.onCatalogOrThreadAccessed(threadDescriptor)
      modifyNavigationHistory.addThread(threadDescriptor)
      updateBookmarkInfoUponThreadOpen.await(threadDescriptor)

      updatePostsParsedOnceJob = viewModelScope.launch {
        delay(250L)
        _postsFullyParsedOnceFlow.emit(true)
      }
    }

    postScreenState.onEndLoading(threadDescriptor)
  }

  suspend fun onCatalogLoadingEnd(catalogDescriptor: CatalogDescriptor?) {
    if (catalogDescriptor != null) {
      savedStateHandle.set(LAST_VISITED_CATALOG_KEY, catalogDescriptor)
    } else {
      savedStateHandle.remove(LAST_VISITED_CATALOG_KEY)
    }

    if (catalogDescriptor != null) {
      chanCache.onCatalogOrThreadAccessed(catalogDescriptor)
      modifyNavigationHistory.addCatalog(catalogDescriptor)

      updatePostsParsedOnceJob = viewModelScope.launch {
        delay(250L)
        _postsFullyParsedOnceFlow.emit(true)
      }
    }

    postScreenState.onEndLoading(catalogDescriptor)
  }

  fun reparsePost(postCellData: PostCellData, parsedPostDataContext: ParsedPostDataContext) {
    reparsePosts(listOf(Pair(postCellData, parsedPostDataContext)))
  }

  fun reparsePosts(postCellDataList: List<Pair<PostCellData, ParsedPostDataContext>>) {
    if (postCellDataList.isEmpty()) {
      return
    }

    viewModelScope.launch(globalConstants.postParserDispatcher) {
      reparsePostsSuspend(postCellDataList)
    }
  }

  fun reparseCatalogPostsWithNewViewMode() {
    reparseCatalogPostsWithNewViewModeJob?.cancel()
    reparseCatalogPostsWithNewViewModeJob = viewModelScope.launch(globalConstants.postParserDispatcher) {
      val allCurrentPosts = (postScreenState.postsAsyncDataState.value as? AsyncData.Data)
        ?.data
        ?.postsCopy
        ?: return@launch

      val catalogPostViewMode = appSettings.catalogPostViewMode.read().toPostViewMode()

      val postCellDataList = allCurrentPosts.map { postCellData ->
        val newParsedPostDataContext = postCellData.parsedPostDataContext
          ?.copy(postViewMode = catalogPostViewMode)
          ?: ParsedPostDataContext(
            isParsingCatalog = true,
            postViewMode = catalogPostViewMode
          )

        return@map postCellData to newParsedPostDataContext
      }

      reparsePostsSuspend(postCellDataList)
      reparseCatalogPostsWithNewViewModeJob = null
    }
  }

  private suspend fun reparsePostsSuspend(postCellDataList: List<Pair<PostCellData, ParsedPostDataContext>>) {
    if (postCellDataList.isEmpty()) {
      return
    }

    val chanTheme = themeEngine.chanTheme
    val batchCount = globalConstants.coresCount.coerceAtLeast(2)

    val reparsedPostCellDataList = processDataCollectionConcurrently(
      dataList = postCellDataList,
      batchCount = batchCount,
      dispatcher = globalConstants.postParserDispatcher
    ) { (postCellData, parsedPostDataContext) ->
      val newParsedPostData = parsedPostDataCache.calculateParsedPostData(
        postCellData = postCellData,
        chanTheme = chanTheme,
        parsedPostDataContext = parsedPostDataContext
      )

      return@processDataCollectionConcurrently postCellData.copy(parsedPostData = newParsedPostData)
    }

    postScreenState.insertOrUpdateMany(reparsedPostCellDataList)
  }

  fun reparsePostsByDescriptors(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ) {
    if (postDescriptors.isEmpty()) {
      return
    }

    viewModelScope.launch(globalConstants.postParserDispatcher) {
      reparsePostsByDescriptorsSuspend(
        chanDescriptor = chanDescriptor,
        postDescriptors = postDescriptors
      )
    }
  }

  private suspend fun reparsePostsByDescriptorsSuspend(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ) {
    if (postDescriptors.isEmpty()) {
      return
    }

    val chanTheme = themeEngine.chanTheme

    val postCellDataList = postScreenState.getPosts(postDescriptors)
      .filter { postCellData -> postCellData.parsedPostDataContext != null }

    if (postCellDataList.isEmpty()) {
      return
    }

    postCellDataList.forEach { postCellData ->
      val parsedPostData = parsedPostDataCache.recalculatePostCellData(
        chanDescriptor = chanDescriptor,
        postCellData = postCellData,
        chanTheme = chanTheme,
        parsedPostDataContext = postCellData.parsedPostDataContext!!
      )

      postScreenState.insertOrUpdate(postCellData.copy(parsedPostData = parsedPostData))
    }
  }

  fun reparsePostSubject(postCellData: PostCellData, onPostSubjectParsed: (AnnotatedString?) -> Unit) {
    val parsedPostData = postCellData.parsedPostData
    if (parsedPostData == null) {
      onPostSubjectParsed(null)
     return
    }

    viewModelScope.launch {
      val chanTheme = themeEngine.chanTheme

      val parsedPostSubject = withContext(globalConstants.postParserDispatcher) {
        return@withContext parsedPostDataCache.parseAndProcessPostSubject(
          chanTheme = chanTheme,
          postIndex = postCellData.originalPostOrder,
          postDescriptor = postCellData.postDescriptor,
          postTimeMs = postCellData.timeMs,
          opMark = postCellData.opMark,
          sage = postCellData.sage,
          posterName = postCellData.name,
          posterTripcode = postCellData.tripcode,
          posterId = postCellData.posterId,
          postIcons = arrayOf(postCellData.countryFlag, postCellData.boardFlag).filterNotNull(),
          postImages = postCellData.images,
          postSubjectParsed = parsedPostData.parsedPostSubject,
          archived = postCellData.archived,
          deleted = postCellData.deleted,
          closed = postCellData.closed,
          sticky = postCellData.sticky,
          parsedPostDataContext = parsedPostData.parsedPostDataContext
        )
      }

      onPostSubjectParsed(parsedPostSubject)
    }
  }

  suspend fun parseInitialBatchOfPosts(
    startPostDescriptor: PostDescriptor?,
    chanDescriptor: ChanDescriptor,
    postDataList: List<IPostData>,
    isCatalogMode: Boolean,
    postViewMode: PostViewMode,
    forced: Boolean
  ): List<PostCellData> {
    if (postDataList.isEmpty()) {
      return emptyList()
    }

    val chanTheme = themeEngine.chanTheme
    val initialBatchSize = if (globalUiInfoManager.isTablet) 32 else 16

    return withContext(globalConstants.postParserDispatcher) {
      val startIndex = postDataList
        .indexOfFirst { postData -> postData.postDescriptor == startPostDescriptor }
        .takeIf { index -> index >= 0 }
        ?: 0

      val indexesOfPostsToParse = postDataList.indices
        .toList()
        .bidirectionalSequence(startPosition = startIndex)
        .take(initialBatchSize)
        .toSet()

      logcat {
        "Parsing posts ${indexesOfPostsToParse.size}/${postDataList.size}, " +
          "indexes=[${indexesOfPostsToParse.joinToString()}], startIndex=$startIndex"
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
              isParsingCatalog = isCatalogMode,
              postViewMode = postViewMode
            ),
            forced = forced
          )
        } else {
          null
        }

        resultList += PostCellData.fromPostData(
          chanDescriptor = chanDescriptor,
          postData = postData,
          parsedPostData = parsedPostData
        )
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
    postViewMode: PostViewMode
  ) {
    val chanTheme = themeEngine.chanTheme

    withContext(globalConstants.postParserDispatcher) {
      val startIndex = postCellDataList
        .indexOfFirst { postData -> postData.postDescriptor == startPostDescriptor }
        .takeIf { index -> index >= 0 }
        ?: 0

      val postDataListSlice = postCellDataList.slice(startIndex until (startIndex + count))
      if (postDataListSlice.isEmpty()) {
        return@withContext
      }

      for (postData in postDataListSlice) {
        calculatePostData(
          chanDescriptor = chanDescriptor,
          postData = postData,
          chanTheme = chanTheme,
          parsedPostDataContext = ParsedPostDataContext(
            isParsingCatalog = isCatalogMode,
            postViewMode = postViewMode
          )
        )
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  fun parseRemainingPostsAsync(
    chanDescriptor: ChanDescriptor,
    postDataList: List<IPostData>,
    postViewMode: PostViewMode,
    parsePostsOptions: ParsePostsOptions = ParsePostsOptions(),
    sorter: suspend (Collection<PostCellData>) -> List<PostCellData>,
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
      val chunkSize = ((postDataList.size / chunksCount) + 1).coerceAtLeast(chunksCount)
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
        logcat(TAG) {
          "parseRemainingPostsAsync() starting parsing ${postDataList.size} posts, " +
            "(chunksCount=$chunksCount, chunkSize=$chunkSize)"
        }

        supervisorScope {
          postDataList
            .bidirectionalSequenceIndexed(startPosition = 0)
            .chunked(chunkSize)
            .mapIndexed { chunkIndex, postDataListChunk ->
              return@mapIndexed async(globalConstants.postParserDispatcher) {
                logcat(TAG, LogPriority.VERBOSE) {
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
                      isParsingCatalog = isParsingCatalog,
                      postViewMode = postViewMode
                    ),
                    forced = parsePostsOptions.forced
                  )

                  mutex.withLock {
                    resultMap[postData.postDescriptor] = PostCellData.fromPostData(
                      chanDescriptor = chanDescriptor,
                      postData = postData,
                      parsedPostData = parsedPostData
                    )
                  }
                }

                logcat(TAG, LogPriority.VERBOSE) {
                  "parseRemainingPostsAsyncRegular() chunk ${chunkIndex} processing finished"
                }
              }
            }
            .toList()
            .awaitAll()
        }

        logcat(TAG) {
          "parseRemainingPostsAsync() finished parsing ${postDataList.size} posts"
        }

        if (parsePostsOptions.parseRepliesTo) {
          val postDescriptors = postDataList.map { postData -> postData.postDescriptor }
          val postDataMap = postDataList.associateBy { postData -> postData.postDescriptor }
          val repliesToPostDescriptorSet = postReplyChainManager.getManyRepliesTo(postDescriptors)

          val repliesToPostDataSet = hashSetOf<IPostData>()
          repliesToPostDataSet += chanCache.getManyForDescriptor(chanDescriptor, repliesToPostDescriptorSet)
          repliesToPostDataSet += repliesToPostDescriptorSet
            .mapNotNull { postDescriptor -> postDataMap[postDescriptor] }

          val repliesToChunkSize = ((repliesToPostDataSet.size / chunksCount) + 1).coerceAtLeast(chunksCount)

          if (repliesToPostDataSet.isNotEmpty()) {
            logcat(TAG, LogPriority.VERBOSE) {
              "parseRemainingPostsAsyncReplies() starting parsing ${repliesToPostDataSet.size} posts, " +
                "(repliesToPostDescriptorSet=${repliesToPostDescriptorSet.size}, " +
                "chunksCount=$chunksCount, chunkSize=$repliesToChunkSize)"
            }

            supervisorScope {
              repliesToPostDataSet
                .chunked(repliesToChunkSize)
                .mapIndexed { chunkIndex, postDataListChunk ->
                  return@mapIndexed async(globalConstants.postParserDispatcher) {
                    logcat(TAG, LogPriority.VERBOSE) {
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
                          isParsingCatalog = isParsingCatalog,
                          postViewMode = postViewMode,
                        ),
                        forced = true
                      )

                      mutex.withLock {
                        resultMap[postData.postDescriptor] = PostCellData.fromPostData(
                          chanDescriptor = chanDescriptor,
                          postData = postData,
                          parsedPostData = parsedPostData
                        )
                      }
                    }

                    logcat(TAG, LogPriority.VERBOSE) {
                      "parseRemainingPostsAsyncReplies() chunk ${chunkIndex} processing finished"
                    }
                  }
                }
                .toList()
                .awaitAll()
            }

            logcat(TAG, LogPriority.VERBOSE) {
              "parseRemainingPostsAsyncReplies() finished parsing ${repliesToPostDataSet.size} posts"
            }
          }
        }

        ensureActive()
        val postsToSort = resultMap.values.toList()

        logcat(TAG) { "parseRemainingPostsAsync() sorting ${postsToSort.size} posts..." }
        val (postCellDataListSorted, time) = measureTimedValue { sorter(postsToSort) }
        logcat(TAG) { "parseRemainingPostsAsync() sorting ${postsToSort.size} done! Took $time" }

        ensureActive()
        showPostsLoadingSnackbarJob.cancel()
        withContext(NonCancellable) { onPostsParsed(postCellDataListSorted) }

        val deltaTime = SystemClock.elapsedRealtime() - startTime
        logcat(TAG) {
          "parseRemainingPostsAsync() parsing ${postDataList.size} posts... done! Took ${deltaTime} ms"
        }
      } catch (error: Throwable) {
        logcat(TAG) { "parseRemainingPostsAsync() error: ${error.asLog()}" }

        if (error is RuntimeException) {
          throw error
        }
      } finally {
        showPostsLoadingSnackbarJob.cancel()
      }
    }
  }

  private suspend fun calculatePostData(
    chanDescriptor: ChanDescriptor,
    postData: IPostData,
    chanTheme: ChanTheme,
    parsedPostDataContext: ParsedPostDataContext,
    forced: Boolean = false
  ): ParsedPostData {
    return parsedPostDataCache.getOrCalculateParsedPostData(
      chanDescriptor = chanDescriptor,
      postData = postData,
      parsedPostDataContext = parsedPostDataContext,
      chanTheme = chanTheme,
      forced = forced
    )
  }

  fun scrollTop() {
    viewModelScope.launch {
      _postListScrollEventFlow.emit(ToolbarScrollEvent.ScrollTop)
    }
  }

  fun scrollBottom() {
    viewModelScope.launch {
      _postListScrollEventFlow.emit(ToolbarScrollEvent.ScrollBottom)
    }
  }

  fun scrollToPost(postDescriptor: PostDescriptor, blink: Boolean = false) {
    viewModelScope.launch {
      _postListScrollEventFlow.emit(ToolbarScrollEvent.ScrollToItem(postDescriptor, blink))
    }
  }

  fun updateSearchQuery(searchQuery: String?) {
    postScreenState.onSearchQueryUpdated(searchQuery)
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

  open fun resetTimer() {

  }

  fun onPostListDisposed() {
    postScreenState.onPostListDisposed(chanDescriptor)
  }

  suspend fun restoreScrollPosition() {
    val currentChanDescriptor = chanDescriptor
      ?: return

    restoreScrollPosition(
      chanDescriptor = currentChanDescriptor,
      scrollToPost = null
    )

    postScreenState.onContentDrawnOnce(chanDescriptor)
  }

  fun rememberPosition(
    chanDescriptor: ChanDescriptor,
    orientation: Int,
    index: Int,
    offset: Int
  ) {
    if (!postScreenState.contentLoadedAndDrawnOnce) {
      return
    }

    val wasAlreadyRemembered = lazyColumnRememberedPositionCache.contains(chanDescriptor)

    val rememberedPosition = lazyColumnRememberedPositionCache.getOrPut(
      key = chanDescriptor,
      defaultValue = {
        LazyColumnRememberedPosition(
          index = index,
          offset = offset,
          orientation = orientation
        )
      }
    )

    if (index <= 0 && offset <= 0 && wasAlreadyRemembered) {
      return
    }

    rememberedPosition.index = index
    rememberedPosition.offset = offset
    rememberedPosition.orientation = orientation

    lazyColumnRememberedPositionCache[chanDescriptor] = rememberedPosition
  }

  fun resetPosition(chanDescriptor: ChanDescriptor) {
    lazyColumnRememberedPositionCache.remove(chanDescriptor)
  }

  open fun onPostScrollChanged(
    firstVisiblePostData: PostCellData,
    lastVisiblePostData: PostCellData,
    postListTouchingBottom: Boolean
  ) {

  }

  protected suspend fun restoreScrollPosition(
    chanDescriptor: ChanDescriptor,
    scrollToPost: PostDescriptor?
  ) {
    if (scrollToPost != null) {
      val posts = (postScreenState.postsAsyncDataState.value as? AsyncData.Data)?.data?.postsCopy?.toList()
        ?: emptyList()

      val index = posts
        .indexOfLast { postCellData -> postCellData.postDescriptor == scrollToPost }

      logcat(tag = TAG) {
        "restoreScrollPosition($chanDescriptor, $scrollToPost) " +
          "scrollToPost: ${scrollToPost}, postsCount: ${posts.size}, " +
          "index: $index"
      }

      if (index >= 0) {
        val blinkPostDescriptor = posts.getOrNull(index)?.postDescriptor

        globalUiInfoManager.orientations.forEach { orientation ->
          val newLastRememberedPosition = LazyColumnRememberedPositionEvent(
            orientation = orientation,
            index = index,
            offset = 0,
            blinkPostDescriptor = blinkPostDescriptor
          )

          _scrollRestorationEventFlow.emit(newLastRememberedPosition)
        }
      }

      return
    }

    val lastRememberedPosition = lazyColumnRememberedPositionCache[chanDescriptor]?.toLazyColumnRememberedPositionEvent()
    if (lastRememberedPosition != null) {
      logcat(tag = TAG) {
        "restoreScrollPosition($chanDescriptor, $scrollToPost) " +
          "lastRememberedPosition: ${lastRememberedPosition}"
      }

      _scrollRestorationEventFlow.emit(lastRememberedPosition)
      return
    }

    val posts = (postScreenState.postsAsyncDataState.value as? AsyncData.Data)?.data?.postsCopy
    val lastViewedPostForScrollRestoration = postScreenState.lastViewedPostForScrollRestoration.value

    if (posts != null && lastViewedPostForScrollRestoration != null) {
      val index = posts
        .indexOfLast { postCellData -> postCellData.postDescriptor == lastViewedPostForScrollRestoration.postDescriptor }

      logcat(tag = TAG) {
        "restoreScrollPosition($chanDescriptor, $scrollToPost) " +
          "lastViewedPostForScrollRestoration: ${lastViewedPostForScrollRestoration}, " +
          "postsCount: ${posts.size}, index: $index"
      }

      if (index >= 0) {
        globalUiInfoManager.orientations.forEach { orientation ->
          val newLastRememberedPosition = LazyColumnRememberedPositionEvent(
            orientation = orientation,
            index = index,
            offset = 0,
            blinkPostDescriptor = lastViewedPostForScrollRestoration.postDescriptor
              .takeIf { lastViewedPostForScrollRestoration.blink }
          )

          _scrollRestorationEventFlow.emit(newLastRememberedPosition)
        }
      }

      return
    }

    logcat(tag = TAG) { "restoreScrollPosition($chanDescriptor, $scrollToPost) Nothing to restore" }
  }

  data class ParsePostsOptions(
    val forced: Boolean = false,
    // If set to true then along with new posts the posts these new posts reply to will be re-parsed
    // as well. This is needed to update the "X replies" PostCell text.
    val parseRepliesTo: Boolean = false,
  )

  data class LoadOptions(
    val showLoadingIndicator: Boolean = true,
    val forced: Boolean = false,
    val deleteCached: Boolean = false,
    val loadFromNetwork: Boolean = true,
    val scrollToPost: PostDescriptor? = null
  )

  sealed class ToolbarScrollEvent {
    object ScrollTop : ToolbarScrollEvent()
    object ScrollBottom : ToolbarScrollEvent()

    data class ScrollToItem(
      val postDescriptor: PostDescriptor,
      val blink: Boolean
    ) : ToolbarScrollEvent()
  }

  companion object {
    private const val TAG = "PostScreenViewModel"

    const val LAST_VISITED_CATALOG_KEY = "last_visited_catalog"
    const val LAST_VISITED_THREAD_KEY = "last_visited_thread"
  }

}