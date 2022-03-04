package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor

abstract class AbstractPostsState {
  protected var _postsCopy: List<PostData>? = null
  protected var _lastUpdatedOn: Long = 0
  val lastUpdatedOn: Long
    get() = _lastUpdatedOn

  abstract val chanDescriptor: ChanDescriptor
  abstract val posts: List<State<PostData>>
  protected abstract val postsMutable: SnapshotStateList<MutableState<PostData>>

  abstract fun update(postData: PostData)
  abstract fun mergePostsWith(newThreadPosts: List<PostData>): PostsMergeResult

  fun updateSearchQuery(searchQuery: String?) {
    if (searchQuery == null) {
      if (_postsCopy != null) {
        val oldThreads = _postsCopy!!.map { mutableStateOf(it) }

        postsMutable.clear()
        postsMutable.addAll(oldThreads)
      }

      _postsCopy = null
      return
    }

    if (_postsCopy == null) {
      _postsCopy = postsMutable.map { it.value.copy() }
    }

    val filteredThreads = _postsCopy!!.filter { postData ->
      if (searchQuery.isEmpty()) {
        return@filter true
      }

      val commentMatchesQuery = postData.postCommentParsedAndProcessed
        ?.text
        ?.contains(other = searchQuery, ignoreCase = true)
        ?: false

      if (commentMatchesQuery) {
        return@filter true
      }

      val subjectMatchesQuery = postData.postSubjectParsedAndProcessed
        ?.text
        ?.contains(other = searchQuery, ignoreCase = true)
        ?: false

      if (subjectMatchesQuery) {
        return@filter true
      }

      return@filter false
    }

    postsMutable.clear()
    postsMutable.addAll(filteredThreads.map { mutableStateOf(it) })
  }
}

data class PostsMergeResult(
  val newPostsCount: Int,
  val newOrUpdatedPostsToReparse: List<PostData>
) {

  fun isNotEmpty(): Boolean {
    return newPostsCount > 0
  }

  fun info(): String {
    return "newPostsCount=${newPostsCount}, newOrUpdatedPostsToReparseCount=${newOrUpdatedPostsToReparse.size}"
  }

  companion object {
    val EMPTY = PostsMergeResult(0, emptyList())
  }

}