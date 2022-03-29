package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state

import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.toHashSetByKey
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import org.koin.java.KoinJavaComponent.inject

class PostsState(
  val chanDescriptor: ChanDescriptor
) {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  @Volatile private var _lastUpdatedOn: Long = 0
  val lastUpdatedOn: Long
    get() = _lastUpdatedOn

  private val postIndexes = linkedMapOf<PostDescriptor, Int>()

  private val postsMutable = mutableStateListOf<MutableState<PostCellData>>()
  val posts: List<State<PostCellData>>
    get() = postsMutable

  val postListAnimationInfoMap = mutableStateMapOf<PostDescriptor, PreviousPostDataInfo>()

  fun insertOrUpdate(postCellData: PostCellData) {
    Snapshot.withMutableSnapshot {
      val index = postIndexes[postCellData.postDescriptor]
      if (index == null) {
        val nextPostIndex = postIndexes.values.maxOrNull()?.plus(1) ?: 0
        postIndexes[postCellData.postDescriptor] = nextPostIndex

        // We assume that posts can only be inserted at the end of the post list
        postsMutable += mutableStateOf(postCellData)
      } else {
        _lastUpdatedOn = SystemClock.elapsedRealtime()
        postsMutable[index].value = postCellData
      }

      check(postIndexes.size == postsMutable.size) {
        "postIndexes.size (${postIndexes.size}) != postsMutable.size (${postsMutable.size})"
      }

      if (androidHelpers.isDevFlavor()) {
        val postMutableDeduplicated = postsMutable.toHashSetByKey { it.value.postDescriptor }
        check(postMutableDeduplicated.size == postsMutable.size) {
          "Duplicates found in postsMutable " +
            "postMutableDeduplicated.size=${postMutableDeduplicated.size}, " +
            "postsMutable.size=${postsMutable.size})"
        }
      }

      updatePostListAnimationInfoMap(listOf(postCellData))
    }
  }

  fun insertOrUpdateMany(postCellDataCollection: Collection<PostCellData>) {
    if (postCellDataCollection.isEmpty()) {
      return
    }

    Snapshot.withMutableSnapshot {
      var initialIndex = postIndexes.values.maxOrNull()?.plus(1) ?: 0

      for (postCellData in postCellDataCollection) {
        val postDescriptor = postCellData.postDescriptor

        val index = postIndexes[postDescriptor]
        if (index == null) {
          postIndexes[postDescriptor] = initialIndex++

          // We assume that posts can only be inserted at the end of the post list
          postsMutable += mutableStateOf(postCellData)
        } else {
          postsMutable[index].value = postCellData
        }
      }

      check(postIndexes.size == postsMutable.size) {
        "postIndexes.size (${postIndexes.size}) != postsMutable.size (${postsMutable.size})"
      }

      if (androidHelpers.isDevFlavor()) {
        val postMutableDeduplicated = postsMutable.toHashSetByKey { it.value.postDescriptor }
        check(postMutableDeduplicated.size == postsMutable.size) {
          "Duplicates found in postsMutable " +
            "postMutableDeduplicated.size=${postMutableDeduplicated.size}, " +
            "postsMutable.size=${postsMutable.size})"
        }
      }

      _lastUpdatedOn = SystemClock.elapsedRealtime()
      updatePostListAnimationInfoMap(postCellDataCollection)
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

  private fun updatePostListAnimationInfoMap(postDataList: Collection<PostCellData>) {
    // Pre-insert first batch of posts into the previousPostDataInfoMap so that we don't play
    // animations for recently opened catalogs/threads. We are doing this right inside of the
    // composition because otherwise there is some kind of a delay before LaunchedEffect is executed
    // so the first posts are always animated.

    val now = SystemClock.elapsedRealtime()

    postDataList.forEach { postDataState ->
      postDataState.postServerDataHashForListAnimations?.let { hash ->
        postListAnimationInfoMap[postDataState.postDescriptor] = PreviousPostDataInfo(hash, now)
      }
    }
  }

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