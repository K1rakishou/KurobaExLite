package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.AbstractPostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsMergeResult
import logcat.logcat

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

  override fun update(postData: PostData) {
    val index = _threadPosts.indexOfFirst { it.value.postDescriptor == postData.postDescriptor }
    if (index < 0) {
      return
    }

    _threadPosts[index].value = postData
  }

  override fun mergePostsWith(newThreadPosts: List<PostData>): PostsMergeResult {
    val prevThreadPostMap = _threadPosts.associateBy { it.value.postDescriptor }

    val postsToUpdate = mutableListOf<Pair<Int, PostData>>()
    val postsToInsert = mutableListOf<PostData>()

    for ((index, newThreadPost) in newThreadPosts.withIndex()) {
      val prevThreadPostState = prevThreadPostMap[newThreadPost.postDescriptor]
      if (prevThreadPostState == null) {
        postsToInsert += newThreadPost
        continue
      }

      val prevThreadPost = prevThreadPostState.value
      if (prevThreadPost.differsWith(newThreadPost)) {
        postsToUpdate += Pair(index, newThreadPost)
      }
    }

    if (postsToUpdate.isNotEmpty()) {
      for ((index, postDataToUpdate) in postsToUpdate) {
        _threadPosts[index].value = postDataToUpdate
      }
    }

    if (postsToInsert.isNotEmpty()) {
      _threadPosts.addAll(postsToInsert.map { mutableStateOf(it) })
    }

    logcat { "postsToUpdateCount=${postsToUpdate.size}, postsToInsertCount=${postsToInsert.size}" }

    val newOrUpdatedPostsToReparse = mutableListWithCap<PostData>(postsToUpdate.size + postsToInsert.size)
    newOrUpdatedPostsToReparse.addAll(postsToUpdate.map { it.second })
    newOrUpdatedPostsToReparse.addAll(postsToInsert)

    return PostsMergeResult(
      newPostsCount = postsToInsert.size,
      newOrUpdatedPostsToReparse = newOrUpdatedPostsToReparse
    )
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