package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor

abstract class AbstractPostsState {
  private var postsCopy: List<PostData>? = null

  abstract val chanDescriptor: ChanDescriptor
  abstract val posts: List<State<PostData>>
  protected abstract val postsMutable: SnapshotStateList<MutableState<PostData>>

  abstract fun update(postData: PostData)
  abstract fun mergePostsWith(newThreadPosts: List<PostData>): PostsMergeResult

  fun updateSearchQuery(searchQuery: String?) {
    if (searchQuery == null) {
      if (postsCopy != null) {
        val oldThreads = postsCopy!!.map { mutableStateOf(it) }

        postsMutable.clear()
        postsMutable.addAll(oldThreads)
      }

      postsCopy = null
      return
    }

    if (postsCopy == null) {
      postsCopy = postsMutable.map { it.value.copy() }
    }

    if (searchQuery.isNotEmpty()) {
      val filteredThreads = postsCopy!!.filter { postData ->
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
}

data class PostsMergeResult(
  val newPostsCount: Int,
  val newOrUpdatedPostsToReparse: List<PostData>
) {

  fun isNotEmpty(): Boolean {
    return newPostsCount > 0
  }

}