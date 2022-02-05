package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat

class ThreadScreenViewModel(
  private val chanThreadManager: ChanThreadManager,
  globalConstants: GlobalConstants,
  postCommentParser: PostCommentParser,
  postCommentApplier: PostCommentApplier,
  themeEngine: ThemeEngine
) : PostScreenViewModel(globalConstants, postCommentParser, postCommentApplier, themeEngine) {
  private val threadScreenState = ThreadScreenState()
  private var loadThreadJob: Job? = null

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload() {
    val currentlyOpenedThread = chanThreadManager.currentlyOpenedThread

    loadThread(
      threadDescriptor = currentlyOpenedThread,
      forced = true
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

        loadThreadInternal(threadDescriptor = threadDescriptor, forced = true)
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

      val mergeResult = threadPostsAsync.data.mergePostsWith(threadData.threadPosts)

      parseRemainingPostsAsync(
        isCatalogMode = false,
        postDataList = mergeResult.newOrUpdatedPostsToReparse,
        onStartParsingPosts = {
          pushCatalogOrThreadPostsLoadingSnackbar(
            postsCount = mergeResult.newOrUpdatedPostsToReparse.size
          )
        },
        onPostsParsed = { postDataList ->
          popCatalogOrThreadPostsLoadingSnackbar()
        }
      )

      threadScreenState.threadCellDataState.value = formatThreadCellData(threadData, null)
      threadScreenState.chanDescriptorState.value = threadDescriptor

      logcat {
        "refresh($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
          "threadPosts=${threadData.threadPosts.size}, " +
          "newPostsCount=${mergeResult.newPostsCount}"
      }
    }
  }

  fun loadThread(
    threadDescriptor: ThreadDescriptor?,
    forced: Boolean = false
  ) {
    loadThreadJob?.cancel()
    loadThreadJob = null

    loadThreadJob = viewModelScope.launch { loadThreadInternal(threadDescriptor, forced) }
  }

  private suspend fun loadThreadInternal(
    threadDescriptor: ThreadDescriptor?,
    forced: Boolean
  ) {
    if (!forced && chanThreadManager.currentlyOpenedThread == threadDescriptor) {
      return
    }

    val startTime = SystemClock.elapsedRealtime()
    threadScreenState.postsAsyncDataState.value = AsyncData.Loading

    val threadDataResult = chanThreadManager.loadThread(threadDescriptor)
    if (threadDataResult.isFailure) {
      val error = threadDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      return
    }

    val threadData = threadDataResult.unwrap()
    if (threadData == null || threadDescriptor == null) {
      threadScreenState.postsAsyncDataState.value = AsyncData.Empty
      return
    }

    if (threadData.threadPosts.isEmpty()) {
      val error = ThreadDisplayException("Thread /${threadDescriptor}/ has no posts")
      threadScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      return
    }

    parsePostsAround(
      startIndex = 0,
      postDataList = threadData.threadPosts,
      count = 16,
      isCatalogMode = false
    )

    parseRemainingPostsAsync(
      isCatalogMode = false,
      postDataList = threadData.threadPosts,
      onStartParsingPosts = {
        pushCatalogOrThreadPostsLoadingSnackbar(
          postsCount = threadData.threadPosts.size
        )
      },
      onPostsParsed = { postDataList ->
        popCatalogOrThreadPostsLoadingSnackbar()
      }
    )

    val threadPostsState = ThreadPostsState(
      threadDescriptor = threadDescriptor,
      threadPosts = threadData.threadPosts
    )

    threadScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
    postScreenState.threadCellDataState.value = formatThreadCellData(threadData, null)
    postScreenState.chanDescriptorState.value = threadDescriptor

    logcat {
      "loadThread($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
        "threadPosts=${threadData.threadPosts.size}"
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


}