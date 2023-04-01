package com.github.k1rakishou.kurobaexlite.features.posts.shared

import android.os.SystemClock
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.filtering.PostHideHelper
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.PostBindProcessorCoordinator
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.bidirectionalSequence
import com.github.k1rakishou.kurobaexlite.helpers.util.bidirectionalSequenceIndexed
import com.github.k1rakishou.kurobaexlite.helpers.util.buffer
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.parallelForEach
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.UpdateBookmarkInfoUponThreadOpen
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.interactors.filtering.HideOrUnhidePost
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.LoadMarkedPosts
import com.github.k1rakishou.kurobaexlite.interactors.navigation.ModifyNavigationHistory
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.FastScrollerMarksManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
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
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository
import com.github.k1rakishou.kurobaexlite.ui.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalTime::class)
abstract class PostScreenViewModel(
  protected val savedStateHandle: SavedStateHandle
) : BaseViewModel() {
  protected val postReplyChainRepository: IPostReplyChainRepository by inject(IPostReplyChainRepository::class.java)
  protected val chanPostCache: IChanPostCache by inject(IChanPostCache::class.java)
  protected val chanThreadManager: ChanThreadManager by inject(ChanThreadManager::class.java)
  protected val parsedPostDataRepository: ParsedPostDataRepository by inject(ParsedPostDataRepository::class.java)
  protected val postBindProcessorCoordinator: PostBindProcessorCoordinator by inject(PostBindProcessorCoordinator::class.java)
  protected val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  protected val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  protected val appSettings: AppSettings by inject(AppSettings::class.java)
  protected val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  protected val mediaViewerPostListScroller: MediaViewerPostListScroller by inject(MediaViewerPostListScroller::class.java)
  protected val modifyNavigationHistory: ModifyNavigationHistory by inject(ModifyNavigationHistory::class.java)
  protected val loadMarkedPosts: LoadMarkedPosts by inject(LoadMarkedPosts::class.java)
  protected val loadChanCatalog: LoadChanCatalog by inject(LoadChanCatalog::class.java)
  protected val updateBookmarkInfoUponThreadOpen: UpdateBookmarkInfoUponThreadOpen by inject(UpdateBookmarkInfoUponThreadOpen::class.java)
  protected val fastScrollerMarksManager: FastScrollerMarksManager by inject(FastScrollerMarksManager::class.java)
  protected val postHideHelper: PostHideHelper by inject(PostHideHelper::class.java)
  protected val postHideRepository: IPostHideRepository by inject(IPostHideRepository::class.java)
  protected val hideOrUnhidePost: HideOrUnhidePost by inject(HideOrUnhidePost::class.java)

  private var currentParseJob: Job? = null
  private var updatePostsParsedOnceJob: Job? = null
  private var reparsePostsWithNewContextJobs = mutableMapOf<String, Job>()

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

    viewModelScope.launch {
      postBindProcessorCoordinator.pendingPostsForReparsingFlow
        .buffer(delay = 1.seconds, emitIfEmpty = false)
        .collect { postDescriptors ->
          reparsePostDescriptorSuspend(postDescriptors)
        }
    }

    viewModelScope.launch {
      postHideRepository.postsToReparseFlow
        .buffer(delay = 100.milliseconds, emitIfEmpty = false)
        .collect { postDescriptors ->
          reparsePostDescriptorSuspend(postDescriptors.flatten())
        }
    }

    viewModelScope.launch {
      appSettings.globalFontSizeMultiplier.listen(eagerly = false)
        .collect { globalFontSizeMultiplier ->
          val isParsingCatalog = when (chanDescriptor) {
            is CatalogDescriptor -> true
            is ThreadDescriptor -> false
            else -> return@collect
          }

          val postViewMode = currentPostViewMode()
          val postCommentFontSizePixels = appSettings.calculateFontSizeInPixels(16)

          logcat(TAG) {
            "globalFontSizeMultiplier changed: ${globalFontSizeMultiplier}, " +
              "reparsing posts (isParsingCatalog: ${isParsingCatalog}, " +
              "postViewMode: ${postViewMode}, " +
              "postCommentFontSizePixels: ${postCommentFontSizePixels})"
          }

          reparseCurrentPostsWithNewContext { parsedPostDataContext ->
            if (parsedPostDataContext != null) {
              return@reparseCurrentPostsWithNewContext parsedPostDataContext
                .copy(postCommentFontSizePixels = postCommentFontSizePixels)
            }

            return@reparseCurrentPostsWithNewContext ParsedPostDataContext(
              isParsingCatalog = isParsingCatalog,
              postViewMode = postViewMode,
              postCommentFontSizePixels = postCommentFontSizePixels
            )
          }
        }
    }
  }

  override fun onCleared() {
    super.onCleared()

    currentParseJob?.cancel()
    currentParseJob = null

    updatePostsParsedOnceJob?.cancel()
    updatePostsParsedOnceJob = null

    reparsePostsWithNewContextJobs.values.forEach { it.cancel() }
    reparsePostsWithNewContextJobs.clear()
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
      postHideRepository.deleteOlderThanThreeMonths()
        .onFailure { error ->
          logcatError(TAG) { "deleteOlderThanThreeMonths() error: ${error.asLogIfImportantOrErrorMessage()}" }
        }
        .onSuccess { deleted ->
          if (deleted >= 0) {
            logcat(TAG) { "deleteOlderThanThreeMonths() -> ${deleted}" }
          }
        }

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
      postHideRepository.deleteOlderThanThreeMonths()
        .onFailure { error ->
          logcatError(TAG) { "deleteOlderThanThreeMonths() error: ${error.asLogIfImportantOrErrorMessage()}" }
        }
        .onSuccess { deleted ->
          if (deleted >= 0) {
            logcat(TAG) { "deleteOlderThanThreeMonths() -> ${deleted}" }
          }
        }

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
      chanPostCache.onCatalogOrThreadAccessed(threadDescriptor)
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
      chanPostCache.onCatalogOrThreadAccessed(catalogDescriptor)
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

    viewModelScope.launch(postParserDispatcher) {
      reparsePostsSuspend(postCellDataList)
    }
  }

  fun reparseCurrentPostsWithNewContext(
    parsedPostDataContextBuilder: suspend (ParsedPostDataContext?) -> ParsedPostDataContext
  ) {
    val jobId = this::class.java.name
    logcat(TAG, LogPriority.VERBOSE) { "reparseCurrentPostsWithNewContext() jobId: ${jobId}" }

    reparsePostsWithNewContextJobs[jobId]?.cancel()
    reparsePostsWithNewContextJobs[jobId] = viewModelScope.launch(postParserDispatcher) {
      val allCurrentPosts = (postScreenState.postsAsyncDataState.value as? AsyncData.Data)
        ?.data
        ?.postsCopy
        ?: return@launch

      val postCellDataList = allCurrentPosts.map { postCellData ->
        val newParsedPostDataContext = parsedPostDataContextBuilder(postCellData.parsedPostDataContext)
        return@map postCellData to newParsedPostDataContext
      }

      reparsePostsSuspend(postCellDataList)
      reparsePostsWithNewContextJobs.remove(jobId)
    }
  }

  private suspend fun reparsePostDescriptorSuspend(
    postDescriptors: List<PostDescriptor>
  ) {
    val postCellDataList = postScreenState.getPosts(postDescriptors)
    if (postCellDataList.isEmpty()) {
      return
    }

    val toParse = postCellDataList.mapNotNull { postCellData ->
      val parsedPostDataContext = postCellData.parsedPostDataContext
        ?: return@mapNotNull null

      return@mapNotNull postCellData to parsedPostDataContext
    }

    reparsePostsSuspend(toParse)
  }

  private suspend fun reparsePostsSuspend(postCellDataList: List<Pair<PostCellData, ParsedPostDataContext>>) {
    if (postCellDataList.isEmpty()) {
      return
    }

    val chanTheme = themeEngine.chanTheme
    val parallelization = AppConstants.coresCount.coerceAtLeast(2)
    val chanDescriptor = postCellDataList.first().first.chanDescriptor

    val reparsedPostCellDataList = parallelForEach(
      dataList = postCellDataList,
      parallelization = parallelization,
      dispatcher = postParserDispatcher
    ) { (postCellData, parsedPostDataContext) ->
      val newParsedPostData = parsedPostDataRepository.calculateParsedPostData(
        postCellData = postCellData,
        chanTheme = chanTheme,
        parsedPostDataContext = parsedPostDataContext
      )

      return@parallelForEach postCellData.copy(parsedPostData = newParsedPostData)
    }

    val filteredPostCellDataList = postHideHelper.filterPosts(
      chanDescriptor = chanDescriptor,
      changedPosts = reparsedPostCellDataList,
      postCellDataByPostDescriptor = { postDescriptor -> postScreenState.getPost(postDescriptor) }
    )

    postScreenState.insertOrUpdateMany(filteredPostCellDataList)
  }

  fun reparsePostsByDescriptors(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ) {
    if (postDescriptors.isEmpty()) {
      return
    }

    viewModelScope.launch(postParserDispatcher) {
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

    val reparsedPostCellDataList = postCellDataList.map { postCellData ->
      val parsedPostData = parsedPostDataRepository.recalculatePostCellData(
        chanDescriptor = chanDescriptor,
        postCellData = postCellData,
        chanTheme = chanTheme,
        parsedPostDataContext = postCellData.parsedPostDataContext!!
      )

      return@map postCellData.copy(parsedPostData = parsedPostData)
    }

    val filteredPostCellDataList = postHideHelper.filterPosts(
      chanDescriptor = chanDescriptor,
      changedPosts = reparsedPostCellDataList,
      postCellDataByPostDescriptor = { postDescriptor -> postScreenState.getPost(postDescriptor) }
    )

    postScreenState.insertOrUpdateMany(filteredPostCellDataList)
  }

  fun reparsePostSubject(postCellData: PostCellData, onPostSubjectParsed: (AnnotatedString?) -> Unit) {
    val parsedPostData = postCellData.parsedPostData
    if (parsedPostData == null) {
      onPostSubjectParsed(null)
     return
    }

    viewModelScope.launch {
      val chanTheme = themeEngine.chanTheme

      val parsedPostSubject = withContext(postParserDispatcher) {
        return@withContext parsedPostDataRepository.parseAndProcessPostSubject(
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

    return withContext(postParserDispatcher) {
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
      val postCommentFontSizePixels = appSettings.calculateFontSizeInPixels(16)

      for (index in postDataList.indices) {
        val postData = postDataList[index]

        val parsedPostData = if (index in indexesOfPostsToParse) {
          calculatePostData(
            chanDescriptor = chanDescriptor,
            postData = postData,
            chanTheme = chanTheme,
            parsedPostDataContext = ParsedPostDataContext(
              isParsingCatalog = isCatalogMode,
              postViewMode = postViewMode,
              postCommentFontSizePixels = postCommentFontSizePixels
            ),
            forced = forced
          )
        } else {
          null
        }

        val postHideUi = postHideRepository.postHideForPostDescriptor(postData.postDescriptor)
          ?.toPostHideUi()

        resultList += PostCellData.fromPostData(
          chanDescriptor = chanDescriptor,
          postData = postData,
          parsedPostData = parsedPostData,
          postHideUi = postHideUi
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
    val postCommentFontSizePixels = appSettings.calculateFontSizeInPixels(16)

    withContext(postParserDispatcher) {
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
            postViewMode = postViewMode,
            postCommentFontSizePixels = postCommentFontSizePixels
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

    currentParseJob = viewModelScope.launch(postParserDispatcher) {
      if (postDataList.isEmpty()) {
        withContext(NonCancellable) { onPostsParsed(emptyList()) }
        return@launch
      }

      val chunksCount = AppConstants.coresCount.coerceAtLeast(2)
      val chunkSize = ((postDataList.size / chunksCount) + 1).coerceAtLeast(chunksCount)
      val chanTheme = themeEngine.chanTheme
      val isParsingCatalog = chanDescriptor is CatalogDescriptor
      val postCommentFontSizePixels = appSettings.calculateFontSizeInPixels(16)

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
              return@mapIndexed async(postParserDispatcher) {
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
                      postViewMode = postViewMode,
                      postCommentFontSizePixels = postCommentFontSizePixels
                    ),
                    forced = parsePostsOptions.forced
                  )

                  val postHideUi = postHideRepository.postHideForPostDescriptor(postData.postDescriptor)
                    ?.toPostHideUi()

                  mutex.withLock {
                    resultMap[postData.postDescriptor] = PostCellData.fromPostData(
                      chanDescriptor = chanDescriptor,
                      postData = postData,
                      parsedPostData = parsedPostData,
                      postHideUi = postHideUi
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
          val repliesToPostDescriptorSet = postReplyChainRepository.getManyRepliesTo(postDescriptors)

          val repliesToPostDataSet = hashSetOf<IPostData>()
          repliesToPostDataSet += chanPostCache.getManyForDescriptor(chanDescriptor, repliesToPostDescriptorSet)
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
                  return@mapIndexed async(postParserDispatcher) {
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
                          postCommentFontSizePixels = postCommentFontSizePixels
                        ),
                        forced = true
                      )

                      val postHideUi = postHideRepository.postHideForPostDescriptor(postData.postDescriptor)
                        ?.toPostHideUi()

                      mutex.withLock {
                        resultMap[postData.postDescriptor] = PostCellData.fromPostData(
                          chanDescriptor = chanDescriptor,
                          postData = postData,
                          parsedPostData = parsedPostData,
                          postHideUi = postHideUi
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
        val (postCellDataListSorted, time1) = measureTimedValue { sorter(postsToSort) }
        logcat(TAG) { "parseRemainingPostsAsync() sorting ${postsToSort.size} done! Took $time1" }

        logcat(TAG) { "parseRemainingPostsAsync() filtering ${postsToSort.size} posts..." }
        val postCellDataListFiltered = postHideHelper.filterPosts(
          chanDescriptor = chanDescriptor,
          changedPosts = postCellDataListSorted,
          postCellDataByPostDescriptor = null
        )
        logcat(TAG) { "parseRemainingPostsAsync() filtered ${postsToSort.size} done!" }

        ensureActive()
        showPostsLoadingSnackbarJob.cancel()
        withContext(NonCancellable) { onPostsParsed(postCellDataListFiltered) }

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
    return parsedPostDataRepository.getOrCalculateParsedPostData(
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

    val postsParsedOnce = postsFullyParsedOnceFlow.value
    if (!postsParsedOnce) {
      return
    }

    val catalogMode = descriptor is CatalogDescriptor

    postBindProcessorCoordinator.onPostBind(
      isCatalogMode = catalogMode,
      postDescriptor = postCellData.postDescriptor
    )
  }

  fun onPostUnbind(postCellData: PostCellData) {
    val descriptor = chanDescriptor
      ?: return

    val catalogMode = descriptor is CatalogDescriptor

    postBindProcessorCoordinator.onPostUnbind(
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

  fun unhidePost(postDescriptor: PostDescriptor) {
    viewModelScope.launch { hideOrUnhidePost.unhide(postDescriptor) }
  }

  private suspend fun currentPostViewMode(): PostViewMode {
    if (this is CatalogScreenViewModel) {
      return appSettings.catalogPostViewMode.read().toPostViewMode()
    }

    return PostViewMode.List
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

    @Suppress("MoveLambdaOutsideParentheses")
    private val postParserDispatcher by lazy {
      val threadNameFormat = "post_parser_thread_%d"
      val mThreadId = AtomicInteger(0)

      return@lazy Executors.newFixedThreadPool(
        AppConstants.coresCount.coerceAtLeast(2),
        { runnable ->
          val thread = Thread(runnable)
          thread.name = String.format(threadNameFormat, mThreadId.getAndIncrement())

          return@newFixedThreadPool thread
        }
      ).asCoroutineDispatcher()
    }
  }

}