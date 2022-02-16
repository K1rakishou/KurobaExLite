package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

abstract class AbstractPostsState {
  private var postsCopy: List<PostData>? = null

  protected val mutex = Mutex()
  abstract val chanDescriptor: ChanDescriptor
  abstract val posts: List<State<PostData>>
  protected abstract val postsMutable: SnapshotStateList<MutableState<PostData>>

  abstract suspend fun update(postData: PostData)
  abstract suspend fun mergePostsWith(newThreadPosts: List<PostData>): PostsMergeResult

  suspend fun updateSearchQuery(searchQuery: String?) {
    withContext(Dispatchers.Default) {
      mutex.withLock {
        if (searchQuery == null) {
          if (postsCopy != null) {
            val oldThreads = postsCopy!!.map { mutableStateOf(it) }

            postsMutable.clear()
            postsMutable.addAll(oldThreads)
          }

          postsCopy = null
          return@withLock
        }

        if (postsCopy == null) {
          postsCopy = postsMutable.map { it.value.copy() }
        }

        if (searchQuery.isNotEmpty()) {
          val filteredThreads = postsCopy!!.filter { postData ->
            val commentMatchesQuery = postData.postCommentParsedAndProcessed
              ?.let { comment -> comment.text.contains(searchQuery) }
              ?: false

            if (commentMatchesQuery) {
              return@filter true
            }

            val subjectMatchesQuery = postData.postSubjectParsedAndProcessed
              ?.let { subject -> subject.text.contains(searchQuery) }
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