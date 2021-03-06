package com.github.k1rakishou.kurobaexlite.features.posts.thread

import android.os.SystemClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.ThreadScreenPostsState
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.executors.RendezvousCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.sort.ThreadPostSorter
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.AddOrRemoveBookmark
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.UpdatePostSeenForBookmark
import com.github.k1rakishou.kurobaexlite.interactors.navigation.LoadNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.UpdateChanThreadView
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.managers.LastVisitedEndpointManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadStatusCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repoository.CatalogPagesRepository
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class ThreadScreenViewModel(
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(savedStateHandle) {
  private val screenKey: ScreenKey = ThreadScreen.SCREEN_KEY

  private val loadChanThreadView: LoadChanThreadView by inject(LoadChanThreadView::class.java)
  private val updateChanThreadView: UpdateChanThreadView by inject(UpdateChanThreadView::class.java)
  private val crossThreadFollowHistory: CrossThreadFollowHistory by inject(CrossThreadFollowHistory::class.java)
  private val lastVisitedEndpointManager: LastVisitedEndpointManager by inject(LastVisitedEndpointManager::class.java)
  private val loadNavigationHistory: LoadNavigationHistory by inject(LoadNavigationHistory::class.java)
  private val addOrRemoveBookmark: AddOrRemoveBookmark by inject(AddOrRemoveBookmark::class.java)
  private val updatePostSeenForBookmark: UpdatePostSeenForBookmark by inject(UpdatePostSeenForBookmark::class.java)
  private val catalogPagesRepository: CatalogPagesRepository by inject(CatalogPagesRepository::class.java)
  private val applicationVisibilityManager: ApplicationVisibilityManager by inject(ApplicationVisibilityManager::class.java)

  private val threadAutoUpdater = ThreadAutoUpdater(
    applicationVisibilityManager = applicationVisibilityManager,
    executeUpdate = { refresh() },
    canUpdate = {
      return@ThreadAutoUpdater threadScreenState.postsAsyncDataState.value is AsyncData.Data &&
        threadScreenState.currentSearchQuery == null
    }
  )

  private val threadScreenState = ThreadScreenPostsState()
  private var loadThreadJob: Job? = null
  private val updateChanThreadViewExecutor = DebouncingCoroutineExecutor(viewModelScope)
  private val bookmarkThreadExecutor = RendezvousCoroutineExecutor(viewModelScope)

  override val postScreenState: PostScreenState = threadScreenState

  val timeUntilNextUpdateMs: Long
    get() = threadAutoUpdater.timeUntilNextUpdateMs
  val threadDescriptor: ThreadDescriptor?
    get() = chanDescriptor as? ThreadDescriptor

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    loadPrevVisitedThread()
  }

  private suspend fun loadPrevVisitedThread() {
    val lastVisitedThreadDescriptor = savedStateHandle.get<ThreadDescriptor>(LAST_VISITED_THREAD_KEY)
    if (lastVisitedThreadDescriptor != null) {
      logcat(tag = TAG) { "loadPrevVisitedThread() got ${lastVisitedThreadDescriptor} from savedStateHandle" }

      loadThread(
        threadDescriptor = lastVisitedThreadDescriptor,
        loadOptions = LoadOptions(forced = true),
        onReloadFinished = null
      )

      return
    }

    val lastVisitedThread = loadNavigationHistory.loadLastVisitedThread()
    if (lastVisitedThread != null) {
      logcat(tag = TAG) { "loadPrevVisitedThread() got ${lastVisitedThread} from loadNavigationHistory" }

      lastVisitedEndpointManager.notifyRestoreLastVisitedThread(lastVisitedThread)
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
        sorter = { postCellDataCollection -> ThreadPostSorter.sortThreadPostCellData(postCellDataCollection) },
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
            onPostsParsed(
              threadDescriptor = threadDescriptor,
              postLoadResult = postLoadResult
            )

            threadScreenState.threadCellDataState.value = formatThreadCellData(
              postLoadResult = postLoadResult,
              prevThreadStatusCellData = prevCellDataState
            )
          } finally {
            withContext(Dispatchers.Main) {
              snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()
              onRefreshFinished?.invoke()
            }
          }
        }
      )
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

    snackbarManager.popSnackbar(SnackbarId.ReloadLastVisitedThread)

    loadThreadJob = viewModelScope.launch {
      resetTimer()
      loadThreadInternal(threadDescriptor, loadOptions, onReloadFinished)
    }
  }

  fun bookmarkOrUnbookmarkThread() {
    bookmarkThreadExecutor.post {
      val bookmarkDescriptor = threadDescriptor
        ?: return@post

      val bookmarkTitle = parsedPostDataCache.formatThreadToolbarTitle(bookmarkDescriptor.toOriginalPostDescriptor())
        ?: return@post

      val bookmarkThumbnail = chanCache.getOriginalPost(bookmarkDescriptor)
        ?.images
        ?.firstOrNull()
        ?.thumbnailUrl
        ?: return@post

      addOrRemoveBookmark.addOrRemoveBookmark(
        threadDescriptor = bookmarkDescriptor,
        bookmarkTitle = bookmarkTitle,
        bookmarkThumbnail = bookmarkThumbnail
      )
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
    globalUiInfoManager.onLoadingErrorUpdatedOrRemoved(screenKey, false)

    if (threadDescriptor != null) {
      val lastViewedPostDescriptor = loadChanThreadView.execute(threadDescriptor)?.lastViewedPDForScroll

      resetPosition(threadDescriptor)
      threadScreenState.lastViewedPostForScrollRestoration.value = lastViewedPostDescriptor
    }

    if (loadOptions.deleteCached && threadDescriptor != null) {
      chanThreadManager.delete(threadDescriptor)
    }

    val postLoadResultMaybe = chanThreadManager.loadThread(threadDescriptor)
    if (postLoadResultMaybe.isFailure) {
      val error = postLoadResultMaybe.exceptionOrThrow()
      logcatError { "loadThreadInternal() error=${error.asLog()}" }

      threadAutoUpdater.stopAutoUpdaterLoop()
      threadScreenState.lastLoadErrorState.value = error
      threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      globalUiInfoManager.onLoadingErrorUpdatedOrRemoved(screenKey, true)

      onThreadLoadingEnd(threadDescriptor)

      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val postLoadResult = postLoadResultMaybe.unwrap()
    if (postLoadResult == null || threadDescriptor == null) {
      threadScreenState.postsAsyncDataState.value = AsyncData.Uninitialized
      onThreadLoadingEnd(threadDescriptor)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    if (postLoadResult.isEmpty()) {
      val postsAsyncData = threadScreenState.postsAsyncDataState.value
      onThreadLoadingEnd(threadDescriptor)
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
    val allCombinedPosts = postLoadResult.allCombinedForThread()

    val startParsePost = if (loadOptions.scrollToPost != null) {
      // Set the lastViewedPostForScrollRestoration to scrollToPost so that we can actually start from
      // there
      threadScreenState.lastViewedPostForScrollRestoration.value = loadOptions.scrollToPost
      loadOptions.scrollToPost
    } else {
      threadScreenState.lastViewedPostForScrollRestoration.value
    }

    if (cachedThreadPostsState != null && cachedThreadPostsState.chanDescriptor == chanDescriptor) {
      logcat(TAG) {
        "loadThreadInternal() Merging cached posts with new posts. Info: ${postLoadResult.info()}"
      }

      val initiallyParsedPosts = parseInitialBatchOfPosts(
        startPostDescriptor = startParsePost,
        chanDescriptor = threadDescriptor,
        postDataList = allCombinedPosts,
        isCatalogMode = false,
        forced = false
      )

      threadScreenState.insertOrUpdateMany(initiallyParsedPosts)
    } else {
      logcat(TAG) {
        "loadThreadInternal() No cached posts, using posts from the server. Info: ${postLoadResult.info()}"
      }

      val initiallyParsedPosts = parseInitialBatchOfPosts(
        startPostDescriptor = startParsePost,
        chanDescriptor = threadDescriptor,
        postDataList = allCombinedPosts,
        isCatalogMode = false,
        forced = false
      )

      val threadPostsState = PostsState(threadDescriptor)

      Snapshot.withMutableSnapshot {
        threadScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
        threadScreenState.insertOrUpdateMany(initiallyParsedPosts)
      }
    }

    snackbarManager.popThreadNewPostsSnackbar()
    postScreenState.threadCellDataState.value = formatThreadCellData(
      postLoadResult = postLoadResult,
      prevThreadStatusCellData = prevCellDataState
    )

    parseRemainingPostsAsync(
      chanDescriptor = threadDescriptor,
      postDataList = allCombinedPosts,
      parsePostsOptions = ParsePostsOptions(parseRepliesTo = true),
      sorter = { postCellDataCollection -> ThreadPostSorter.sortThreadPostCellData(postCellDataCollection) },
      onStartParsingPosts = {
        snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
          postsCount = postLoadResult.newPostsCount,
          screenKey = screenKey
        )
      },
      onPostsParsed = { postDataList ->
        logcat {
          "loadThreadInternal($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
            "allCombinedPosts=${allCombinedPosts.size}, postDataList=${postDataList.size}"
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
    if (postLoadResult.isNotEmpty()) {
      val lastViewedOrLoadedPostDescriptor = loadChanThreadView.execute(threadDescriptor)
        ?.let { chanThreadView ->
          return@let chanThreadView.lastViewedPDForNewPosts
            ?: chanThreadView.lastLoadedPostDescriptor
        }

      if (lastViewedOrLoadedPostDescriptor != null) {
        snackbarManager.pushThreadNewPostsSnackbar(
          newPostsCount = postLoadResult.newPostsCountSinceLastViewedOrLoaded(
            lastViewedOrLoadedPostDescriptor = lastViewedOrLoadedPostDescriptor
          ),
          updatedPostsCount = postLoadResult.updatePostsCountExcludingOriginalPost,
          // TODO(KurobaEx): for now we can't use this because it will show "N deleted posts" snackbar
          //  on each refresh. We need to somehow check that we have already notified the user about
          //  these deleted posts so that we don't notify him on every thread update.
//          deletedPostsCount = postLoadResult.deletedPostsCount,
          deletedPostsCount = 0,
          screenKey = screenKey
        )
      }
    }

    updateLastLoadedAndViewedPosts(
      key = "onPostsParsed",
      threadDescriptor = threadDescriptor,
      firstVisiblePostDescriptor = null,
      lastVisiblePostDescriptor = null,
      postListTouchingBottom = false
    )

    val originalPost = postLoadResult.firstOrNull { postData -> postData is OriginalPostData }
    if (originalPost != null && (originalPost.archived || originalPost.closed || originalPost.deleted)) {
      logcat {
        "refresh($threadDescriptor) stopping auto-updater, " +
          "archived=${originalPost.archived}, " +
          "closed=${originalPost.closed}, " +
          "deleted=${originalPost.deleted}"
      }

      threadAutoUpdater.stopAutoUpdaterLoop()
    } else {
      if (postLoadResult.newPostsCount > 0) {
        threadAutoUpdater.resetTimer()
      }

      threadAutoUpdater.runAutoUpdaterLoop(threadDescriptor)
    }
  }

  override fun onPostScrollChanged(
    firstVisiblePostData: PostCellData,
    lastVisiblePostData: PostCellData,
    postListTouchingBottom: Boolean
  ) {
    if (postListTouchingBottom) {
      snackbarManager.popThreadNewPostsSnackbar()
    }

    updateLastLoadedAndViewedPosts(
      key = "onPostScrollChanged",
      threadDescriptor = firstVisiblePostData.postDescriptor.threadDescriptor,
      firstVisiblePostDescriptor = firstVisiblePostData.postDescriptor,
      lastVisiblePostDescriptor = lastVisiblePostData.postDescriptor,
      postListTouchingBottom = postListTouchingBottom
    )

    updatePostSeenForBookmark.onPostViewed(lastVisiblePostData.postDescriptor)
  }

  private fun updateLastLoadedAndViewedPosts(
    key: String,
    threadDescriptor: ThreadDescriptor,
    firstVisiblePostDescriptor: PostDescriptor?,
    lastVisiblePostDescriptor: PostDescriptor?,
    postListTouchingBottom: Boolean?
  ) {
    updateChanThreadViewExecutor.post(timeout = 200L, key = key) {
      val contentLoaded = postScreenState.contentLoaded.value
      val lastThreadPost = chanCache.getLastPost(threadDescriptor)?.postDescriptor

      val firstVisiblePost = if (contentLoaded) {
        firstVisiblePostDescriptor
      } else {
        null
      }

      val lastVisiblePost = if (contentLoaded) {
        lastVisiblePostDescriptor
      } else {
        null
      }

      val updatedChanThreadView = updateChanThreadView.execute(
        threadDescriptor = threadDescriptor,
        threadLastPostDescriptor = lastThreadPost,
        firstVisiblePost = firstVisiblePost,
        lastVisiblePost = lastVisiblePost,
        postListTouchingBottom = postListTouchingBottom
      )

      threadScreenState.lastViewedPostForIndicator.value =
        updatedChanThreadView?.lastViewedPDForIndicator
    }
  }

  private suspend fun formatThreadCellData(
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
    val sticky = originalPostData?.sticky?.toPostCellDataSticky() ?: prevThreadStatusCellData?.sticky
    val bumpLimit = originalPostData?.bumpLimit ?: prevThreadStatusCellData?.bumpLimit
    val imageLimit = originalPostData?.imageLimit ?: prevThreadStatusCellData?.imageLimit

    val threadPageFromRepo = originalPostData?.postDescriptor?.threadDescriptor
      ?.let { threadDescriptor -> catalogPagesRepository.getThreadPage(threadDescriptor) }

    val threadPage = threadPageFromRepo ?: prevThreadStatusCellData?.threadPage

    return ThreadStatusCellData(
      totalReplies = totalReplies,
      totalImages = totalImages,
      totalPosters = totalPosters,
      threadPage = threadPage,
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