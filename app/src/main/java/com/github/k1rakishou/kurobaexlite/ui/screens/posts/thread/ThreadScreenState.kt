package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.IPostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class ThreadScreenState : PostScreenViewModel.PostScreenState {
  override val postsAsyncDataState = MutableStateFlow<AsyncData<IPostsState>>(AsyncData.Empty)
  override val chanDescriptorState = MutableStateFlow<ChanDescriptor?>(null)
  override val threadCellDataState = MutableStateFlow<ThreadCellData?>(null)

  override fun updatePost(postData: PostData) {
    val asyncData = postsAsyncDataState.value
    if (asyncData is AsyncData.Data) {
      asyncData.data.update(postData)
    }
  }

}