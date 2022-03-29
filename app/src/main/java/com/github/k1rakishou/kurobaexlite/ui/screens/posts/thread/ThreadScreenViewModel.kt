package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.sort.ThreadPostSorter
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.interactors.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.interactors.UpdateChanThreadView
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PostsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class ThreadScreenViewModel(
  application: KurobaExLiteApplication,
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(application, savedStateHandle) {
  private val screenKey: ScreenKey = ThreadScreen.SCREEN_KEY

  private val loadChanThreadView: LoadChanThreadView by inject(LoadChanThreadView::class.java)
  private val updateChanThreadView: UpdateChanThreadView by inject(UpdateChanThreadView::class.java)

  private val threadAutoUpdater = ThreadAutoUpdater(
    executeUpdate = { refresh() },
    canUpdate = {
      return@ThreadAutoUpdater threadScreenState.postsAsyncDataState.value is AsyncData.Data &&
        threadScreenState.searchQueryFlow.value == null
    }
  )

  private val threadScreenState = PostScreenState()
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

  override fun refresh(onRefreshFinished: (() -> Unit)?) {
    loadThreadJob?.cancel()
    loadThreadJob = null

    loadThreadJob = viewModelScope.launch {
      val threadPostsAsync = threadScreenState.postsAsyncDataState.value
      val threadDescriptor = chanThreadManager.currentlyOpenedThread
      val prevCellDataState = threadScreenState.threadCellDataState.value

      val threadPostsState = if (threadPostsAsync !is AsyncData.Data) {
        if (threadDescriptor == null) {
          onRefreshFinished?.invoke()
          return@launch
        }

        loadThreadInternal(
          threadDescriptor = threadDescriptor,
          loadOptions = LoadOptions(forced = true),
          onReloadFinished = null
        )

        onRefreshFinished?.invoke()
        return@launch
      } else {
        threadPostsAsync.data
      }

      val startTime = SystemClock.elapsedRealtime()
      logcat { "refresh($threadDescriptor)" }

      val postLoadResultMaybe = chanThreadManager.loadThread(threadDescriptor)
      if (postLoadResultMaybe.isFailure) {
        val error = postLoadResultMaybe.exceptionOrThrow()
        logcatError { "refresh($threadDescriptor) error=${error.asLog()}" }

        threadAutoUpdater.stopAutoUpdaterLoop()
        threadScreenState.threadCellDataState.value = formatThreadCellData(
          postLoadResult = null,
          prevCellDataState = prevCellDataState,
          lastLoadError = error
        )

        onRefreshFinished?.invoke()
        return@launch
      }

      val postLoadResult = postLoadResultMaybe.unwrap()
      if (postLoadResult == null || threadDescriptor == null) {
        onRefreshFinished?.invoke()
        return@launch
      }

      parseRemainingPostsAsync(
        chanDescriptor = threadDescriptor,
        postDataList = postLoadResult.newAndUpdatedCombined(),
        parsePostsOptions = ParsePostsOptions(
          forced = true,
          parseRepliesTo = true
        ),
        sorter = { postCellData -> ThreadPostSorter.sortThreadPostCellData(postCellData) },
        onStartParsingPosts = {
          snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
            postsCount = postLoadResult.newOrUpdatedCount,
            screenKey = screenKey
          )
        },
        onPostsParsed = { postDataList ->
          logcat {
            "refresh($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
              "threadPosts=${postDataList.size}"
          }

          try {
            threadPostsState.insertOrUpdateMany(postDataList)
            snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()

            onPostsParsed(
              threadDescriptor = threadDescriptor,
              postLoadResult = postLoadResult,
              isInitialThreadLoad = false
            )
          } finally {
            withContext(Dispatchers.Main) { onRefreshFinished?.invoke() }
          }
        }
      )

      threadScreenState.threadCellDataState.value = formatThreadCellData(
        postLoadResult = postLoadResult,
        prevCellDataState = prevCellDataState,
        lastLoadError = null
      )

      if (postLoadResult.isNotEmpty()) {
        snackbarManager.pushThreadNewPostsSnackbar(
          newPostsCount = postLoadResult.newPostsCount,
          screenKey = screenKey
        )
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

    if (loadOptions.deleteCached && threadDescriptor != null) {
      chanThreadManager.delete(threadDescriptor)
    }

    val postLoadResultMaybe = chanThreadManager.loadThread(threadDescriptor)
    if (postLoadResultMaybe.isFailure) {
      val error = postLoadResultMaybe.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      threadAutoUpdater.stopAutoUpdaterLoop()
      threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val postLoadResult = postLoadResultMaybe.unwrap()
    if (postLoadResult == null || threadDescriptor == null) {
      threadScreenState.postsAsyncDataState.value = AsyncData.Empty
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    if (postLoadResult.isEmpty()) {
      val postsAsyncData = threadScreenState.postsAsyncDataState.value
      _postsFullyParsedOnceFlow.emit(true)

      if (loadOptions.showLoadingIndicator || postsAsyncData !is AsyncData.Data) {
        val error = ThreadDisplayException("Thread /${threadDescriptor}/ has no posts")

        threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)
        onReloadFinished?.invoke()
      } else {
        onReloadFinished?.invoke()
      }

      return
    }

    val cachedThreadPostsState = (threadScreenState.postsAsyncDataState.value as? AsyncData.Data)?.data
    val allCombinedPosts = postLoadResult.allCombined()

    val threadPostsState = if (cachedThreadPostsState != null && cachedThreadPostsState.chanDescriptor == chanDescriptor) {
      logcat(tag = "loadThreadInternal") { "Merging cached posts with new posts. Info=${postLoadResult.info()}" }

      val initiallyParsedPosts = parseInitialBatchOfPosts(
        startPostDescriptor = threadScreenState.lastViewedPostDescriptorForScrollRestoration.value,
        chanDescriptor = threadDescriptor,
        postDataList = allCombinedPosts,
        isCatalogMode = false
      )

      cachedThreadPostsState.insertOrUpdateMany(initiallyParsedPosts)
      cachedThreadPostsState
    } else {
      logcat(tag = "loadThreadInternal") { "No cached posts, using posts from the server." }

      val initiallyParsedPosts = parseInitialBatchOfPosts(
        startPostDescriptor = threadScreenState.lastViewedPostDescriptorForScrollRestoration.value,
        chanDescriptor = threadDescriptor,
        postDataList = allCombinedPosts,
        isCatalogMode = false
      )

      val threadPostsState = PostsState(threadDescriptor)
      threadPostsState.insertOrUpdateMany(initiallyParsedPosts)

      threadScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
      threadPostsState
    }

    postScreenState.threadCellDataState.value = formatThreadCellData(
      postLoadResult = postLoadResult,
      prevCellDataState = prevCellDataState,
      lastLoadError = null
    )

    postListBuilt?.await()
    restoreScrollPosition(threadDescriptor)

    parseRemainingPostsAsync(
      chanDescriptor = threadDescriptor,
      postDataList =  allCombinedPosts,
      parsePostsOptions = ParsePostsOptions(parseRepliesTo = true),
      sorter = { postCellData -> ThreadPostSorter.sortThreadPostCellData(postCellData) },
      onStartParsingPosts = {
        snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
          postsCount = postLoadResult.newPostsCount,
          screenKey = screenKey
        )
      },
      onPostsParsed = { postDataList ->
        logcat {
          "loadThread($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
            "newAndUpdatedPosts=${allCombinedPosts.size}, postDataList=${postDataList.size}"
        }

        try {
          threadPostsState.insertOrUpdateMany(postDataList)

          onPostsParsed(
            threadDescriptor = threadDescriptor,
            postLoadResult = postLoadResult,
            isInitialThreadLoad = true
          )

          onThreadLoadingEnd(threadDescriptor)
        } finally {
          withContext(NonCancellable + Dispatchers.Main) {
            onReloadFinished?.invoke()
            snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()
          }
        }
      }
    )
  }

  private suspend fun onPostsParsed(
    threadDescriptor: ThreadDescriptor,
    postLoadResult: PostsLoadResult,
    isInitialThreadLoad: Boolean
  ) {
    if (isInitialThreadLoad) {
      updateLastLoadedAndViewedPosts(
        key = "onPostsParsed",
        threadDescriptor = threadDescriptor,
        boundPostDescriptor = null
      )
    }

    if (postLoadResult.newPostsCount > 0) {
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

    snackbarManager.popThreadNewPostsSnackbar()
  }

  fun onPostListNotTouchingBottom() {
    postListTouchingBottom.value = false
  }

  fun onFirstVisiblePostScrollChanged(postCellData: PostCellData) {
    updateLastLoadedAndViewedPosts(
      key = "onFirstVisiblePostScrollChanged",
      threadDescriptor = postCellData.postDescriptor.threadDescriptor,
      boundPostDescriptor = postCellData.postDescriptor
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
    postLoadResult: PostsLoadResult?,
    prevCellDataState: ThreadCellData?,
    lastLoadError: Throwable?
  ): ThreadCellData {
    val originalPostData = postLoadResult
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