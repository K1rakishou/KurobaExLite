package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared

import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class PostsState(
  val chanDescriptor: ChanDescriptor,
  posts: List<PostCellData>? = null
) {

  @Volatile private var _lastUpdatedOn: Long = 0
  val lastUpdatedOn: Long
    get() = _lastUpdatedOn

  private val postIndexes = linkedMapOf<PostDescriptor, Int>()

  private val postsMutable = mutableStateListOf<MutableState<PostCellData>>()
  val posts: List<State<PostCellData>>
    get() = postsMutable

  init {
    if (posts != null) {
      insertOrUpdateMany(posts)
    }
  }

  fun insertOrUpdate(postCellData: PostCellData) {
    val index = postIndexes[postCellData.postDescriptor]
    if (index == null) {
      val nextPostIndex = postIndexes.values.maxOrNull()?.plus(1) ?: 0
      postIndexes[postCellData.postDescriptor] = nextPostIndex

      // We assume that posts can only be inserted at the end of the post list
      postsMutable += mutableStateOf(postCellData)

      return
    }

    _lastUpdatedOn = SystemClock.elapsedRealtime()
    postsMutable[index].value = postCellData
  }

  fun insertOrUpdateMany(postCellDataCollection: Collection<PostCellData>) {
    if (postCellDataCollection.isEmpty()) {
      return
    }

    Snapshot.withMutableSnapshot {
      var initialIndex = postIndexes.values.maxOrNull() ?: 0

      for (postCellData in postCellDataCollection) {
        val index = postIndexes[postCellData.postDescriptor]
        if (index == null) {
          postIndexes[postCellData.postDescriptor] = initialIndex++

          // We assume that posts can only be inserted at the end of the post list
          postsMutable += mutableStateOf(postCellData)

          continue
        }

        postsMutable[index].value = postCellData
      }

      _lastUpdatedOn = SystemClock.elapsedRealtime()
    }
  }

  // TODO(KurobaEx):
//  fun updateSearchQuery(searchQuery: String?) {
//    if (searchQuery == null) {
//      if (_postsCopy != null) {
//        val oldThreads = _postsCopy!!.map { mutableStateOf(it) }
//
//        postsMutable.clear()
//        postsMutable.addAll(oldThreads)
//      }
//
//      _postsCopy = null
//      return
//    }
//
//    if (_postsCopy == null) {
//      _postsCopy = postsMutable.map { it.value.copy() as PostCellData }
//    }
//
//    val filteredThreads = _postsCopy!!.filter { postData ->
//      if (searchQuery.isEmpty()) {
//        return@filter true
//      }
//
//      val commentMatchesQuery = postData.parsedPostData
//        ?.processedPostComment
//        ?.text
//        ?.contains(other = searchQuery, ignoreCase = true)
//        ?: false
//
//      if (commentMatchesQuery) {
//        return@filter true
//      }
//
//      val subjectMatchesQuery = postData.parsedPostData
//        ?.processedPostSubject
//        ?.text
//        ?.contains(other = searchQuery, ignoreCase = true)
//        ?: false
//
//      if (subjectMatchesQuery) {
//        return@filter true
//      }
//
//      return@filter false
//    }
//
//    postsMutable.clear()
//    postsMutable.addAll(filteredThreads.map { mutableStateOf(it) })
//  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PostsState

    if (chanDescriptor != other.chanDescriptor) return false
    if (posts != other.posts) return false

    return true
  }

  override fun hashCode(): Int {
    var result = chanDescriptor.hashCode()
    result = 31 * result + posts.hashCode()
    return result
  }

}