package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.os.SystemClock
import androidx.compose.runtime.snapshots.Snapshot
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
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadStatusCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.ThreadScreenPostsState
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
  private val crossThreadFollowHistory: CrossThreadFollowHistory by inject(CrossThreadFollowHistory::class.java)

  private val threadAutoUpdater = ThreadAutoUpdater(
    executeUpdate = { refresh() },
    canUpdate = {
      return@ThreadAutoUpdater threadScreenState.postsAsyncDataState.value is AsyncData.Data &&
        threadScreenState.searchQueryFlow.value == null
    }
  )

  private val threadScreenState = ThreadScreenPostsState()
  private var loadThreadJob: Job? = null
  private val updateChanThreadViewExecutor = DebouncingCoroutineExecutor(viewModelScope)

  override val postScreenState: PostScreenState = threadScreenState

  val timeUntilNextUpdateMs: Long
    get() = threadAutoUpdater.timeUntilNextUpdateMs
  val threadDescriptor: ThreadDescriptor?
    get() = chanDescriptor as? ThreadDescriptor

  override suspend fun onViewModelReady() {
    loadPrevVisitedThread()
  }

  private suspend fun loadPrevVisitedThread() {
    val lastVisitedThread = appSettings.lastVisitedThread.read()?.toThreadDescriptor()
    if (lastVisitedThread != null) {
      logcat(tag = TAG) { "loadPrevVisitedThread() loading ${lastVisitedThread} from appSettings.lastVisitedThread" }

      loadThread(
        threadDescriptor = lastVisitedThread,
        loadOptions = LoadOptions(forced = true),
        onReloadFinished = null
      )

      return
    }

    logcat(tag = TAG) { "loadPrevVisitedThread() prevThreadDescriptor is null" }
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

      if (threadPostsAsync !is AsyncData.Data) {
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
      }

      val startTime = SystemClock.elapsedRealtime()
      logcat { "refresh($threadDescriptor)" }
      threadScreenState.lastLoadErrorState.value = null

      val postLoadResultMaybe = chanThreadManager.loadThread(threadDescriptor)
      if (postLoadResultMaybe.isFailure) {
        val error = postLoadResultMaybe.exceptionOrThrow()
        logcatError { "refresh($threadDescriptor) error=${error.asLog()}" }

        threadAutoUpdater.stopAutoUpdaterLoop()

        threadScreenState.lastLoadErrorState.value = error
        threadScreenState.threadCellDataState.value = formatThreadCellData(
          postLoadResult = null,
          prevThreadStatusCellData = prevCellDataState
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
            threadScreenState.insertOrUpdateMany(postDataList)
            snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()

            onPostsParsed(
              threadDescriptor = threadDescriptor,
              postLoadResult = postLoadResult
            )

            threadScreenState.threadCellDataState.value = formatThreadCellData(
              postLoadResult = postLoadResult,
              prevThreadStatusCellData = prevCellDataState
            )
          } finally {
            withContext(Dispatchers.Main) { onRefreshFinished?.invoke() }
          }
        }
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
    val reloadingTheSameThread = chanThreadManager.currentlyOpenedThread == threadDescriptor

    if (!loadOptions.forced && reloadingTheSameThread) {
      onReloadFinished?.invoke()
      return
    }

    val startTime = SystemClock.elapsedRealtime()
    val prevCellDataState = if (reloadingTheSameThread) {
      postScreenState.threadCellDataState.value
    } else {
      null
    }

    postScreenState.lastLoadErrorState.value = null
    onThreadLoadingStart(threadDescriptor, loadOptions)

    if (threadDescriptor != null) {
      val lastViewedPostDescriptor = loadChanThreadView.execute(threadDescriptor)
        ?.let { chanThreadView -> chanThreadView.lastViewedPostDescriptor ?: chanThreadView.lastLoadedPostDescriptor  }

      resetPosition(threadDescriptor)
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
      threadScreenState.lastLoadErrorState.value = error
      threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)

      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val postLoadResult = postLoadResultMaybe.unwrap()
    if (postLoadResult == null || threadDescriptor == null) {
      threadScreenState.postsAsyncDataState.value = AsyncData.Uninitialized
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

    val startParsePost = loadOptions.scrollToPost
      ?: threadScreenState.lastViewedPostDescriptorForScrollRestoration.value

    if (cachedThreadPostsState != null && cachedThreadPostsState.chanDescriptor == chanDescriptor) {
      logcat(tag = "loadThreadInternal") { "Merging cached posts with new posts. Info=${postLoadResult.info()}" }

      val initiallyParsedPosts = parseInitialBatchOfPosts(
        startPostDescriptor = startParsePost,
        chanDescriptor = threadDescriptor,
        postDataList = allCombinedPosts,
        isCatalogMode = false
      )

      threadScreenState.insertOrUpdateMany(initiallyParsedPosts)
    } else {
      logcat(tag = "loadThreadInternal") { "No cached posts, using posts from the server." }

      val initiallyParsedPosts = parseInitialBatchOfPosts(
        startPostDescriptor = startParsePost,
        chanDescriptor = threadDescriptor,
        postDataList = allCombinedPosts,
        isCatalogMode = false
      )

      val threadPostsState = PostsState(threadDescriptor)

      Snapshot.withMutableSnapshot {
        threadScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
        threadScreenState.insertOrUpdateMany(initiallyParsedPosts)
      }
    }

    postScreenState.threadCellDataState.value = formatThreadCellData(
      postLoadResult = postLoadResult,
      prevThreadStatusCellData = prevCellDataState
    )

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
          threadScreenState.insertOrUpdateMany(postDataList)

          onPostsParsed(
            threadDescriptor = threadDescriptor,
            postLoadResult = postLoadResult
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
    postLoadResult: PostsLoadResult
  ) {
    updateLastLoadedAndViewedPosts(
      key = "onPostsParsed",
      threadDescriptor = threadDescriptor,
      boundPostDescriptor = null,
      postListTouchingBottom = false
    )

    val originalPost = postLoadResult.firstOrNull { postData -> postData is OriginalPostData }
    if (originalPost != null && (originalPost.archived == true || originalPost.closed == true)) {
      logcat {
        "refresh($threadDescriptor) stopping auto-updater " +
          "(archived=${originalPost.archived}, closed=${originalPost.closed})"
      }

      threadAutoUpdater.stopAutoUpdaterLoop()
    } else {
      if (postLoadResult.newPostsCount > 0) {
        threadAutoUpdater.resetTimer()
      }

      threadAutoUpdater.runAutoUpdaterLoop(threadDescriptor)
    }
  }

  fun onFirstVisiblePostScrollChanged(
    postCellData: PostCellData,
    postListTouchingBottom: Boolean
  ) {
    if (postListTouchingBottom) {
      snackbarManager.popThreadNewPostsSnackbar()
    }

    updateLastLoadedAndViewedPosts(
      key = "onFirstVisiblePostScrollChanged",
      threadDescriptor = postCellData.postDescriptor.threadDescriptor,
      boundPostDescriptor = postCellData.postDescriptor,
      postListTouchingBottom = postListTouchingBottom
    )
  }

  private fun updateLastLoadedAndViewedPosts(
    key: String,
    threadDescriptor: ThreadDescriptor,
    boundPostDescriptor: PostDescriptor?,
    postListTouchingBottom: Boolean?
  ) {
    updateChanThreadViewExecutor.post(timeout = 200L, key = key) {
      val lastThreadPost = chanCache.getLastPost(threadDescriptor)?.postDescriptor

      val updatedChanThreadView = updateChanThreadView.execute(
        threadDescriptor = threadDescriptor,
        threadLastPostDescriptor = lastThreadPost,
        threadBoundPostDescriptor = boundPostDescriptor,
        postListTouchingBottom = postListTouchingBottom
      )

      threadScreenState.lastViewedPostDescriptorForIndicator.value =
        updatedChanThreadView?.lastViewedPDForIndicator
    }
  }

  private fun formatThreadCellData(
    postLoadResult: PostsLoadResult?,
    prevThreadStatusCellData: ThreadStatusCellData?,
  ): ThreadStatusCellData {
    val originalPostData = postLoadResult
      ?.firstOrNull { postData -> postData is OriginalPostData } as? OriginalPostData

    val totalReplies = originalPostData?.threadRepliesTotal ?: prevThreadStatusCellData?.totalReplies ?: 0
    val totalImages = originalPostData?.threadImagesTotal ?: prevThreadStatusCellData?.totalImages ?: 0
    val totalPosters = originalPostData?.threadPostersTotal ?: prevThreadStatusCellData?.totalPosters ?: 0
    val archived = originalPostData?.archived ?: prevThreadStatusCellData?.archived
    val closed = originalPostData?.closed ?: prevThreadStatusCellData?.closed
    val sticky = originalPostData?.sticky ?: prevThreadStatusCellData?.sticky
    val bumpLimit = originalPostData?.bumpLimit ?: prevThreadStatusCellData?.bumpLimit
    val imageLimit = originalPostData?.imageLimit ?: prevThreadStatusCellData?.imageLimit

    return ThreadStatusCellData(
      totalReplies = totalReplies,
      totalImages = totalImages,
      totalPosters = totalPosters,
      archived = archived,
      closed = closed,
      sticky = sticky,
      bumpLimit = bumpLimit,
      imageLimit = imageLimit,
    )
  }

  fun onBackPressed(): Boolean {
    val topThreadDescriptor = crossThreadFollowHistory.pop()
      ?: return false

    loadThread(topThreadDescriptor)
    return true
  }

  class ThreadDisplayException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "ThreadScreenViewModel"
  }

}