package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.AbstractPostsState

class ThreadPostsState(
  val threadDescriptor: ThreadDescriptor,
  threadPosts: List<PostData>
) : AbstractPostsState() {
  private val _threadPosts = mutableStateListOf<MutableState<PostData>>()

  override val postsMutable: SnapshotStateList<MutableState<PostData>>
    get() = _threadPosts
  override val posts: List<State<PostData>>
    get() = _threadPosts
  override val chanDescriptor: ChanDescriptor
    get() = threadDescriptor

  init {
    _threadPosts.addAll(threadPosts.map { mutableStateOf(it) })
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadPostsState

    if (threadDescriptor != other.threadDescriptor) return false
    if (_threadPosts != other._threadPosts) return false

    return true
  }

  override fun hashCode(): Int {
    var result = threadDescriptor.hashCode()
    result = 31 * result + _threadPosts.hashCode()
    return result
  }

}