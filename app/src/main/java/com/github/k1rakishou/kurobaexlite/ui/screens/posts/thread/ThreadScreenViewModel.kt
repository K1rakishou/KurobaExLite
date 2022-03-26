package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.interactors.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.interactors.UpdateChanThreadView
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsMergeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class ThreadScreenViewModel(
  private val chanThreadManager: ChanThreadManager,
  private val chanCache: ChanCache,
  application: KurobaExLiteApplication,
  globalConstants: GlobalConstants,
  themeEngine: ThemeEngine,
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(application, globalConstants, themeEngine, savedStateHandle) {
  private val screenKey: ScreenKey = ThreadScreen.SCREEN_KEY

  private val loadChanThreadView: LoadChanThreadView by inject(LoadChanThreadView::class.java)
  private val updateChanThreadView: UpdateChanThreadView by inject(UpdateChanThreadView::class.java)

  private val threadAutoUpdater = ThreadAutoUpdater(
    executeUpdate = { refresh() },
    canUpdate = { threadScreenState.searchQueryFlow.value == null }
  )

  private val threadScreenState = ThreadScreenState()
  private var loadThreadJob: Job? = null

  private val updateChanThreadViewExecutor = DebouncingCoroutineExecutor(viewModelScope)

  override val postScreenState: PostScreenState = threadScreenState

  val timeUntilNextUpdateMs: Long
    get() = threadAutoUpdater.timeUntilNextUpdateMs
  val threadDescriptor: ThreadDescriptor?
    get() = chanDescriptor as? ThreadDescriptor

  override suspend fun onViewModelReady() {
    val prevThreadDescriptor = savedStateHandle.get<ThreadDescriptor>(PREV_THREAD_DESCRIPTOR)
    logcat(tag = TAG) { "onViewModelReady() prevThreadDescriptor=${prevThreadDescriptor}" }

    if (prevThreadDescriptor != null) {
      loadThread(
        threadDescriptor = prevThreadDescriptor,
        loadOptions = LoadOptions(forced = true),
        onReloadFinished = null
      )
    }
  }

  override fun onCleared() {
    super.onCleared()

    threadAutoUpdater.stopAutoUpdaterLoop()
  }

  override fun reload(
    loadOptions: LoadOptions,
    onReloadFinished: (() -> Unit)?
  ) {
    val currentlyOpenedThread = chanThreadManager.currentlyOpenedThread

    loadThread(
      threadDescriptor = currentlyOpenedThread,
      loadOptions = loadOptions.copy(forced = true),
      onReloadFinished = onReloadFinished
    )
  }

  override fun refresh() {
    loadThreadJob?.cancel()
    loadThreadJob = null

    loadThreadJob = viewModelScope.launch {
      val threadPostsAsync = threadScreenState.postsAsyncDataState.value
      val threadDescriptor = chanThreadManager.currentlyOpenedThread
      val prevCellDataState = threadScreenState.threadCellDataState.value

      if (threadPostsAsync !is AsyncData.Data) {
        if (threadDescriptor == null) {
          return@launch
        }

        loadThreadInternal(
          threadDescriptor = threadDescriptor,
          loadOptions = LoadOptions(forced = true),
          onReloadFinished = null
        )

        return@launch
      }

      val startTime = SystemClock.elapsedRealtime()
      logcat { "refresh($threadDescriptor)" }

      val threadDataResult = chanThreadManager.loadThread(threadDescriptor)
      if (threadDataResult.isFailure) {
        val error = threadDataResult.exceptionOrThrow()
        logcatError { "refresh($threadDescriptor) error=${error.asLog()}" }

        threadAutoUpdater.stopAutoUpdaterLoop()
        threadScreenState.threadCellDataState.value = formatThreadCellData(
          threadData = null,
          prevCellDataState = prevCellDataState,
          lastLoadError = error
        )

        return@launch
      }

      val threadData = threadDataResult.unwrap()
      if (threadData == null || threadDescriptor == null) {
        return@launch
      }

      val originalPostIndex = threadData.threadPosts
        .indexOfFirst { postData -> postData is OriginalPostData }
      if (originalPostIndex >= 0) {
        val startPostDescriptor = (threadData.threadPosts.getOrNull(originalPostIndex) as? OriginalPostData)
          ?.postDescriptor

        // We need to always reparse the OP eagerly whenever it changes otherwise stuff depending on
        // it's ParsedPostData will become incorrect (since we reset it to null every time we detect
        // a post received from the server differs from ours.
        parsePosts(
          startPostDescriptor = startPostDescriptor,
          chanDescriptor = threadDescriptor,
          postDataList = threadData.threadPosts,
          count = 1,
          isCatalogMode = false
        )
      }

      val mergeResult = threadPostsAsync.data.mergePostsWith(threadData.threadPosts)

      parseRemainingPostsAsync(
        chanDescriptor = threadDescriptor,
        postDataList = mergeResult.newOrUpdatedPostsToReparse,
        parsePostsOptions = ParsePostsOptions(
          forced = true,
          parseRepliesTo = true
        ),
        onStartParsingPosts = {
          snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
            postsCount = mergeResult.newOrUpdatedPostsToReparse.size,
            screenKey = screenKey
          )
        },
        onPostsParsed = { postDataList ->
          chanCache.insertThreadPosts(threadDescriptor, postDataList)
          snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()

          onPostsParsed(
            threadDescriptor = threadDescriptor,
            mergeResult = mergeResult,
            isInitialThreadLoad = false
          )
        }
      )

      threadScreenState.threadCellDataState.value = formatThreadCellData(
        threadData = threadData,
        prevCellDataState = prevCellDataState,
        lastLoadError = null
      )

      if (mergeResult.isNotEmpty()) {
        snackbarManager.pushThreadNewPostsSnackbar(
          newPostsCount = mergeResult.newPostsCount,
          screenKey = screenKey
        )
      }

      logcat {
        "refresh($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
          "threadPosts=${threadData.threadPosts.size}, " +
          "newPostsCount=${mergeResult.newPostsCount}"
      }
    }
  }

  override fun resetTimer() {
    threadAutoUpdater.resetTimer()
  }

  fun loadThread(
    threadDescriptor: ThreadDescriptor?,
    loadOptions: LoadOptions = LoadOptions(),
    onReloadFinished: (() -> Unit)? = null
  ) {
    loadThreadJob?.cancel()
    loadThreadJob = null

    loadThreadJob = viewModelScope.launch {
      resetTimer()
      loadThreadInternal(threadDescriptor, loadOptions, onReloadFinished)
    }
  }

  private suspend fun loadThreadInternal(
    threadDescriptor: ThreadDescriptor?,
    loadOptions: LoadOptions,
    onReloadFinished: (() -> Unit)?
  ) {
    if (!loadOptions.forced && chanThreadManager.currentlyOpenedThread == threadDescriptor) {
      onReloadFinished?.invoke()
      return
    }

    val startTime = SystemClock.elapsedRealtime()
    val prevCellDataState = postScreenState.threadCellDataState.value
    onThreadLoadingStart(threadDescriptor, loadOptions)

    if (threadDescriptor != null) {
      val lastViewedPostDescriptor = loadChanThreadView.execute(threadDescriptor)
        ?.let { chanThreadView -> chanThreadView.lastViewedPostDescriptor ?: chanThreadView.lastLoadedPostDescriptor  }

      threadScreenState.lastViewedPostDescriptorForScrollRestoration.value = lastViewedPostDescriptor
    }

    val cachedThreadPostsState = if (threadDescriptor != null) {
      val postsFromCache = chanCache.getThreadPosts(threadDescriptor)
      ThreadPostsState(threadDescriptor, postsFromCache)
    } else {
      null
    }

    val threadDataResult = chanThreadManager.loadThread(threadDescriptor)
    if (threadDataResult.isFailure) {
      val error = threadDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      threadAutoUpdater.stopAutoUpdaterLoop()
      threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val threadData = threadDataResult.unwrap()
    if (threadData == null || threadDescriptor == null) {
      threadScreenState.postsAsyncDataState.value = AsyncData.Empty
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    if (threadData.threadPosts.isEmpty()) {
      val error = ThreadDisplayException("Thread /${threadDescriptor}/ has no posts")

      threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    chanCache.insertThreadPosts(threadDescriptor, threadData.threadPosts)

    if (cachedThreadPostsState != null) {
      val mergeResult = cachedThreadPostsState.mergePostsWith(threadData.threadPosts)
      logcat(tag = "loadThreadInternal") { "Merging cached posts with new posts. Info=${mergeResult.info()}" }

      parsePostsAround(
        startPostDescriptor = threadScreenState.lastViewedPostDescriptorForScrollRestoration.value,
        chanDescriptor = threadDescriptor,
        postDataList = mergeResult.newOrUpdatedPostsToReparse,
        isCatalogMode = false
      )

      threadScreenState.postsAsyncDataState.value = AsyncData.Data(cachedThreadPostsState)
      postScreenState.threadCellDataState.value = formatThreadCellData(
        threadData = threadData,
        prevCellDataState = prevCellDataState,
        lastLoadError = null
      )

      postListBuilt?.await()
      restoreScrollPosition(threadDescriptor)

      parseRemainingPostsAsync(
        chanDescriptor = threadDescriptor,
        postDataList = mergeResult.newOrUpdatedPostsToReparse,
        parsePostsOptions = ParsePostsOptions(parseRepliesTo = true),
        onStartParsingPosts = {
          snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
            postsCount = mergeResult.newPostsCount,
            screenKey = screenKey
          )
        },
        onPostsParsed = { postDataList ->
          logcat {
            "loadThread($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
              "threadPosts=${threadData.threadPosts.size}"
          }

          try {
            onPostsParsed(
              threadDescriptor = threadDescriptor,
              mergeResult = mergeResult,
              isInitialThreadLoad = true
            )

            onThreadLoadingEnd(threadDescriptor)
            _postsFullyParsedOnceFlow.emit(true)
          } finally {
            withContext(NonCancellable + Dispatchers.Main) {
              onReloadFinished?.invoke()
              snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()
            }
          }
        }
      )
    } else {
      logcat(tag = "loadThreadInternal") { "No cached posts, using posts from the server." }

      parsePostsAround(
        startPostDescriptor = threadScreenState.lastViewedPostDescriptorForScrollRestoration.value,
        chanDescriptor = threadDescriptor,
        postDataList = threadData.threadPosts,
        isCatalogMode = false
      )

      val threadPostsState = ThreadPostsState(
        threadDescriptor = threadDescriptor,
        threadPosts = threadData.threadPosts
      )

      val mergeResult = PostsMergeResult(
        newPostsCount = threadData.threadPosts.size,
        newOrUpdatedPostsToReparse = emptyList()
      )

      threadScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
      postScreenState.threadCellDataState.value = formatThreadCellData(
        threadData = threadData,
        prevCellDataState = prevCellDataState,
        lastLoadError = null
      )

      postListBuilt?.await()
      restoreScrollPosition(threadDescriptor)

      parseRemainingPostsAsync(
        chanDescriptor = threadDescriptor,
        postDataList = threadData.threadPosts,
        parsePostsOptions = ParsePostsOptions(parseRepliesTo = true),
        onStartParsingPosts = {
          snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
            postsCount = threadData.threadPosts.size,
            screenKey = screenKey
          )
        },
        onPostsParsed = { postDataList ->
          logcat {
            "loadThread($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
              "threadPosts=${threadData.threadPosts.size}"
          }

          try {
            onPostsParsed(
              threadDescriptor = threadDescriptor,
              mergeResult = mergeResult,
              isInitialThreadLoad = true
            )

            onThreadLoadingEnd(threadDescriptor)
            _postsFullyParsedOnceFlow.emit(true)
          } finally {
            withContext(NonCancellable + Dispatchers.Main) {
              onReloadFinished?.invoke()
              snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()
            }
          }
        }
      )
    }
  }

  private suspend fun onPostsParsed(
    threadDescriptor: ThreadDescriptor,
    mergeResult: PostsMergeResult,
    isInitialThreadLoad: Boolean
  ) {
    if (isInitialThreadLoad) {
      updateLastLoadedAndViewedPosts(
        key = "onPostsParsed",
        threadDescriptor = threadDescriptor,
        boundPostDescriptor = null
      )
    }

    if (mergeResult.newPostsCount > 0) {
      threadAutoUpdater.resetTimer()
    }

    threadAutoUpdater.runAutoUpdaterLoop(threadDescriptor)
  }

  fun onPostListTouchingBottom() {
    val threadDescriptor = (chanDescriptor as? ThreadDescriptor)
      ?: return

    postListTouchingBottom.value = true

    updateLastLoadedAndViewedPosts(
      key = "onPostListTouchingBottom",
      threadDescriptor = threadDescriptor,
      boundPostDescriptor = null
    )
  }

  fun onPostListNotTouchingBottom() {
    postListTouchingBottom.value = false
  }

  fun onFirstVisiblePostScrollChanged(postData: PostData) {
    updateLastLoadedAndViewedPosts(
      key = "onFirstVisiblePostScrollChanged",
      threadDescriptor = postData.postDescriptor.threadDescriptor,
      boundPostDescriptor = postData.postDescriptor
    )
  }

  private fun updateLastLoadedAndViewedPosts(
    key: String,
    threadDescriptor: ThreadDescriptor,
    boundPostDescriptor: PostDescriptor?
  ) {
    updateChanThreadViewExecutor.post(timeout = 200L, key = key) {
      val updatedChanThreadView = updateChanThreadView.execute(
        threadDescriptor = threadDescriptor,
        threadLastPostDescriptor = chanCache.getLastPost(threadDescriptor)?.postDescriptor,
        threadBoundPostDescriptor = boundPostDescriptor,
        isBottomOnThreadReached = postListTouchingBottom.value
      )

      if (updatedChanThreadView != null && postListTouchingBottom.value) {
        threadScreenState.lastViewedPostDescriptorForIndicator.value =
          updatedChanThreadView.lastViewedPostDescriptorForIndicator
      }
    }
  }

  private fun formatThreadCellData(
    threadData: ThreadData?,
    prevCellDataState: ThreadCellData?,
    lastLoadError: Throwable?
  ): ThreadCellData {
    val originalPostData = threadData
      ?.threadPosts
      ?.firstOrNull { postData -> postData is OriginalPostData } as? OriginalPostData

    val totalReplies = originalPostData?.threadRepliesTotal ?: prevCellDataState?.totalReplies ?: 0
    val totalImages = originalPostData?.threadImagesTotal ?: prevCellDataState?.totalImages ?: 0
    val totalPosters = originalPostData?.threadPostersTotal ?: prevCellDataState?.totalPosters ?: 0

    return ThreadCellData(
      totalReplies = totalReplies,
      totalImages = totalImages,
      totalPosters = totalPosters,
      lastLoadError = lastLoadError
    )
  }

  class ThreadDisplayException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "ThreadScreenViewModel"

    const val PREV_THREAD_DESCRIPTOR = "prev_thread_descriptor"
  }

}