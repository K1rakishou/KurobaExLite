package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.compose.runtime.*
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.model.ChanDataSource
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import kotlinx.coroutines.launch
import logcat.asLog

class ThreadScreenViewModel(
  private val chanDataSource: ChanDataSource,
  postCommentParser: PostCommentParser,
  postCommentApplier: PostCommentApplier
) : PostScreenViewModel(postCommentParser, postCommentApplier) {
  val threadScreenState = ThreadScreenState()

  override val postScreenState: PostScreenState = threadScreenState

  fun loadThreadFromCatalog(threadDescriptor: ThreadDescriptor, forced: Boolean = false) {
    mainScope.launch { loadThread(threadDescriptor = threadDescriptor, forced = forced) }
  }

  override fun reload() {

  }

  suspend fun loadThread(
    threadDescriptor: ThreadDescriptor,
    forced: Boolean = false
  ) {
    if (!forced && threadScreenState.currentThreadDescriptorOrNull == threadDescriptor) {
      return
    }

    threadScreenState.threadPostsAsync = AsyncData.Loading

    val threadDataResult = chanDataSource.loadThread(threadDescriptor)
    if (threadDataResult.isFailure) {
      val error = threadDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      threadScreenState.threadPostsAsync = AsyncData.Error(error)
      return
    }

    val threadData = threadDataResult.unwrap()
    if (threadData.threadPosts.isEmpty()) {
      val error = ThreadDisplayException("Thread /${threadDescriptor}/ has no posts")
      threadScreenState.threadPostsAsync = AsyncData.Error(error)
      return
    }

    val threadPostsState = ThreadPostsState(
      threadDescriptor = threadDescriptor,
      threadPosts = threadData.threadPosts
    )

    threadScreenState.threadPostsAsync = AsyncData.Data(threadPostsState)
  }

  class ThreadDisplayException(message: String) : ClientException(message)

  class ThreadScreenState(
    private val threadPostsAsyncState: MutableState<AsyncData<ThreadPostsState>> = mutableStateOf(AsyncData.Loading)
  ) : PostScreenState {
    internal var threadPostsAsync by threadPostsAsyncState

    val currentThreadDescriptorOrNull: ThreadDescriptor?
      get() = (threadPostsAsync as? AsyncData.Data)?.data?.threadDescriptor

    override fun postDataAsync(): AsyncData<List<PostData>> {
      return when (val asyncDataStateValue = threadPostsAsyncState.value) {
        is AsyncData.Data -> AsyncData.Data(asyncDataStateValue.data.threadPosts)
        is AsyncData.Error -> AsyncData.Error(asyncDataStateValue.error)
        AsyncData.Loading -> AsyncData.Loading
      }
    }
  }

  class ThreadPostsState(
    val threadDescriptor: ThreadDescriptor,
    threadPosts: List<PostData>
  ) {
    private val _threadPosts = mutableStateListOf<PostData>()
    val threadPosts: List<PostData>
      get() = _threadPosts

    init {
      _threadPosts.addAll(threadPosts)
    }
  }


}