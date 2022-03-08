package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadViewManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
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
  private val chanThreadCache: ChanThreadCache,
  application: KurobaExLiteApplication,
  globalConstants: GlobalConstants,
  themeEngine: ThemeEngine,
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(application, globalConstants, themeEngine, savedStateHandle) {
  private val screenKey: ScreenKey = ThreadScreen.SCREEN_KEY
  private val chanThreadViewManager: ChanThreadViewManager by inject(ChanThreadViewManager::class.java)

  private val threadAutoUpdater = ThreadAutoUpdater(
    executeUpdate = { refresh() },
    canUpdate = { threadScreenState.searchQueryFlow.value == null }
  )

  private val threadScreenState = ThreadScreenState()
  private var loadThreadJob: Job? = null

  override val postScreenState: PostScreenState = threadScreenState

  val timeUntilNextUpdateMs: Long
    get() = threadAutoUpdater.timeUntilNextUpdateMs

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

        threadScreenState.threadCellDataState.value = formatThreadCellData(null, error)
        return@launch
      }

      val threadData = threadDataResult.unwrap()
      if (threadData == null || threadDescriptor == null) {
        return@launch
      }

      val originalPostIndex = threadData.threadPosts
        .indexOfFirst { postData -> postData is OriginalPostData }
      if (originalPostIndex >= 0) {
        // We need to always reparse the OP eagerly whenever it changes otherwise stuff depending on
        // it's ParsedPostData will become incorrect (since we reset it to null every time we detect
        // a post received from the server differs from ours.
        parsePosts(
          startIndex = originalPostIndex,
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
          chanThreadCache.insertThreadPosts(threadDescriptor, postDataList)
          snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()

          onPostsParsed(
            threadDescriptor = threadDescriptor,
            mergeResult = mergeResult,
            isInitialThreadLoad = false
          )
        }
      )

      threadScreenState.threadCellDataState.value = formatThreadCellData(threadData, null)

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

    onLoadingThread(threadDescriptor, loadOptions)

    val startTime = SystemClock.elapsedRealtime()
    _postsFullyParsedOnceFlow.emit(false)

    if (loadOptions.showLoadingIndicator) {
      threadScreenState.postsAsyncDataState.value = AsyncData.Loading
    }

    savedStateHandle.set(PREV_THREAD_DESCRIPTOR, threadDescriptor)

    if (threadDescriptor != null) {
      threadScreenState.lastViewedPostDescriptor.value = chanThreadViewManager.read(threadDescriptor)
        ?.let { chanThreadView -> chanThreadView.lastViewedPostDescriptor ?: chanThreadView.lastLoadedPostDescriptor  }
    }

    val cachedThreadPostsState = if (threadDescriptor != null) {
      val postsFromCache = chanThreadCache.getThreadPosts(threadDescriptor)
      ThreadPostsState(threadDescriptor, postsFromCache)
    } else {
      null
    }

    val threadDataResult = chanThreadManager.loadThread(threadDescriptor)
    if (threadDataResult.isFailure) {
      val error = threadDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

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

    if (cachedThreadPostsState != null) {
      val mergeResult = cachedThreadPostsState.mergePostsWith(threadData.threadPosts)
      logcat(tag = "loadThreadInternal") { "Merging cached posts with new posts. Info=${mergeResult.info()}" }

      parsePostsAround(
        startIndex = 0,
        chanDescriptor = threadDescriptor,
        postDataList = mergeResult.newOrUpdatedPostsToReparse,
        isCatalogMode = false
      )

      threadScreenState.postsAsyncDataState.value = AsyncData.Data(cachedThreadPostsState)
      postScreenState.threadCellDataState.value = formatThreadCellData(threadData, null)

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
            chanThreadCache.insertThreadPosts(threadDescriptor, postDataList)

            onPostsParsed(
              threadDescriptor = threadDescriptor,
              mergeResult = mergeResult,
              isInitialThreadLoad = true
            )

            postListBuilt?.await()
            restoreScrollPosition(threadDescriptor)
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
        startIndex = 0,
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
      postScreenState.threadCellDataState.value = formatThreadCellData(threadData, null)

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
            chanThreadCache.insertThreadPosts(threadDescriptor, postDataList)

            onPostsParsed(
              threadDescriptor = threadDescriptor,
              mergeResult = mergeResult,
              isInitialThreadLoad = true
            )

            postListBuilt?.await()
            restoreScrollPosition(threadDescriptor)
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
      updateLastLoadedAndViewedPosts(threadDescriptor)
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
    viewModelScope.launch { updateLastLoadedAndViewedPosts(threadDescriptor) }
  }

  fun onPostListNotTouchingBottom() {
    postListTouchingBottom.value = true
  }

  private suspend fun updateLastLoadedAndViewedPosts(
    threadDescriptor: ThreadDescriptor
  ) {
    val lastPostDescriptor = chanThreadCache.getLastPost(threadDescriptor)?.postDescriptor
      ?: return
    val isBottomOnThreadReached = postListTouchingBottom.value

    chanThreadViewManager.insertOrUpdate(threadDescriptor) {
      lastLoadedPostDescriptor = lastPostDescriptor

      if (lastViewedPostDescriptor == null || isBottomOnThreadReached) {
        lastViewedPostDescriptor = lastPostDescriptor
        threadScreenState.lastViewedPostDescriptor.value = lastPostDescriptor
      } else {
        threadScreenState.lastViewedPostDescriptor.value = lastViewedPostDescriptor
      }
    }
  }

  private fun formatThreadCellData(threadData: ThreadData?, lastLoadError: Throwable?): ThreadCellData? {
    val originalPostData = threadData
      ?.threadPosts
      ?.firstOrNull { postData -> postData is OriginalPostData } as? OriginalPostData
      ?: return null

    return ThreadCellData(
      totalReplies = originalPostData.threadRepliesTotal ?: 0,
      totalImages = originalPostData.threadImagesTotal ?: 0,
      totalPosters = originalPostData.threadPostersTotal ?: 0,
      lastLoadError = lastLoadError
    )
  }

  class ThreadDisplayException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "ThreadScreenViewModel"

    private const val PREV_THREAD_DESCRIPTOR = "prev_thread_descriptor"
  }

}