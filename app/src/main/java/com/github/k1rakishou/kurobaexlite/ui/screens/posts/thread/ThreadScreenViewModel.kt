package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
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
    threadScreenState.threadPostsAsync = AsyncData.Loading

    val threadDataResult = chanThreadManager.loadThread(threadDescriptor)
    if (threadDataResult.isFailure) {
      val error = threadDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      threadScreenState.threadPostsAsync = AsyncData.Error(error)
      return
    }

    val threadData = threadDataResult.unwrap()
    if (threadData == null || threadDescriptor == null) {
      threadScreenState.threadPostsAsync = AsyncData.Empty
      return
    }

    if (threadData.threadPosts.isEmpty()) {
      val error = ThreadDisplayException("Thread /${threadDescriptor}/ has no posts")
      threadScreenState.threadPostsAsync = AsyncData.Error(error)
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
      onPostsParsed = { postDataList -> postProcessPostDataAfterParsing(postDataList) }
    )

    val threadPostsState = ThreadPostsState(
      threadDescriptor = threadDescriptor,
      threadPosts = threadData.threadPosts
    )


    threadScreenState.threadPostsAsync = AsyncData.Data(threadPostsState)
    _threadCellDataState.value = formatThreadCellData(threadData)
    _chanDescriptorState.value = threadDescriptor

    logcat {
      "loadThread($threadDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
        "threadPosts=${threadData.threadPosts.size}"
    }
  }

  override suspend fun postProcessPostDataAfterParsing(postDataList: List<PostData>) {
    super.postProcessPostDataAfterParsing(postDataList)

    // TODO(KurobaEx): restore scroll position/etc
  }

  private fun formatThreadCellData(threadData: ThreadData): ThreadCellData? {
    val originalPostData = threadData.threadPosts
      .firstOrNull { postData -> postData is OriginalPostData } as? OriginalPostData
      ?: return null

    return ThreadCellData(
      totalReplies = originalPostData.threadRepliesTotal ?: 0,
      totalImages = originalPostData.threadImagesTotal ?: 0,
      totalPosters = originalPostData.threadPostersTotal ?: 0
    )
  }

  class ThreadDisplayException(message: String) : ClientException(message)

  class ThreadScreenState(
    private val threadPostsAsyncState: MutableState<AsyncData<ThreadPostsState>> = mutableStateOf(AsyncData.Empty)
  ) : PostScreenState {
    internal var threadPostsAsync by threadPostsAsyncState

    override fun postDataAsyncState(): AsyncData<List<State<PostData>>> {
      return when (val asyncDataStateValue = threadPostsAsyncState.value) {
        is AsyncData.Data -> AsyncData.Data(asyncDataStateValue.data.threadPosts)
        is AsyncData.Error -> AsyncData.Error(asyncDataStateValue.error)
        AsyncData.Empty -> AsyncData.Empty
        AsyncData.Loading -> AsyncData.Loading
      }
    }

    override fun updatePost(postData: PostData) {
      val asyncData = threadPostsAsyncState.value
      if (asyncData is AsyncData.Data) {
        asyncData.data.update(postData)
      }
    }

  }

  class ThreadPostsState(
    val threadDescriptor: ThreadDescriptor,
    threadPosts: List<PostData>
  ) {
    private val _threadPosts = mutableStateListOf<MutableState<PostData>>()
    val threadPosts: List<State<PostData>>
      get() = _threadPosts

    init {
      _threadPosts.addAll(threadPosts.map { mutableStateOf(it) })
    }


    fun update(postData: PostData) {
      val index = _threadPosts.indexOfFirst { it.value.postDescriptor == postData.postDescriptor }
      if (index < 0) {
        return
      }

      _threadPosts[index].value = postData
    }

  }


}