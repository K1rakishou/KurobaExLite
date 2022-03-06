package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.AbstractPostsState

class ThreadPostsState(
  threadDescriptor: ThreadDescriptor,
  threadPosts: List<PostData>
) : AbstractPostsState(threadDescriptor) {
  private val _threadPosts = mutableStateListOf<MutableState<PostData>>()

  override val postsMutable: SnapshotStateList<MutableState<PostData>>
    get() = _threadPosts
  override val posts: List<State<PostData>>
    get() = _threadPosts

  init {
    _threadPosts.addAll(threadPosts.map { mutableStateOf(it) })
  }

}