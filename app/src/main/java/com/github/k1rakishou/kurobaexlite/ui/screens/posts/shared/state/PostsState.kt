package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state

import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.linkedMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.toHashSetByKey
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.java.KoinJavaComponent.inject

class PostsState(
  val chanDescriptor: ChanDescriptor
) {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  @Volatile private var _lastUpdatedOn: Long = 0
  val lastUpdatedOn: Long
    get() = _lastUpdatedOn

  private val postIndexes = linkedMapWithCap<PostDescriptor, Int>(128)
  private val allPosts = mutableListWithCap<MutableState<PostCellData>>(128)

  private val postsProcessed = mutableStateListOf<MutableState<PostCellData>>()
  val posts: List<State<PostCellData>>
    get() = postsProcessed

  private val searchQueryFlow = MutableStateFlow<String?>(null)

  val postListAnimationInfoMap = mutableStateMapOf<PostDescriptor, PreviousPostDataInfo>()

  fun postIndexByPostDescriptor(postDescriptor: PostDescriptor): Int? {
    return postIndexes[postDescriptor]
  }

  fun onSearchQueryUpdated(searchQuery: String?) {
    if (searchQuery == searchQueryFlow.value || searchQuery.isNullOrEmpty()) {
      postsProcessed.clear()
      postsProcessed.addAll(allPosts)
      return
    }

    val matchedPostCellDataStates = allPosts.mapNotNull { postCellDataState ->
      val postCellData = postCellDataState.value

      if (searchQuery.isEmpty()) {
        return@mapNotNull postCellDataState
      }

      val commentMatchesQuery = postCellData.parsedPostData
        ?.processedPostComment
        ?.text
        ?.contains(other = searchQuery, ignoreCase = true)
        ?: false

      if (commentMatchesQuery) {
        return@mapNotNull postCellDataState
      }

      val subjectMatchesQuery = postCellData.parsedPostData
        ?.processedPostSubject
        ?.text
        ?.contains(other = searchQuery, ignoreCase = true)
        ?: false

      if (subjectMatchesQuery) {
        return@mapNotNull postCellDataState
      }

      return@mapNotNull null
    }

    postsProcessed.clear()
    postsProcessed.addAll(matchedPostCellDataStates)

    searchQueryFlow.value = searchQuery
  }

  /**
   * Do not call directly! Use PostScreenState.insertOrUpdate() instead!
   * */
  fun insertOrUpdate(postCellData: PostCellData) {
    Snapshot.withMutableSnapshot {
      val index = postIndexes[postCellData.postDescriptor]
      if (index == null) {
        val nextPostIndex = postIndexes.values.maxOrNull()?.plus(1) ?: 0
        postIndexes[postCellData.postDescriptor] = nextPostIndex

        // We assume that posts can only be inserted at the end of the post list
        allPosts += mutableStateOf(postCellData)
      } else {
        _lastUpdatedOn = SystemClock.elapsedRealtime()
        allPosts[index].value = postCellData
      }

      check(postIndexes.size == allPosts.size) {
        "postIndexes.size (${postIndexes.size}) != postsMutable.size (${allPosts.size})"
      }

      if (androidHelpers.isDevFlavor()) {
        val postMutableDeduplicated = allPosts.toHashSetByKey { postCellDataState ->
          postCellDataState.value.postDescriptor
        }

        check(postMutableDeduplicated.size == allPosts.size) {
          "Duplicates found in postsMutable " +
            "postMutableDeduplicated.size=${postMutableDeduplicated.size}, " +
            "postsMutable.size=${allPosts.size})"
        }
      }

      updatePostListAnimationInfoMap(listOf(postCellData))
      onSearchQueryUpdated(searchQueryFlow.value)
    }
  }

  /**
   * Do not call directly! Use PostScreenState.insertOrUpdateMany() instead!
   * */
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
          allPosts += mutableStateOf(postCellData)
        } else {
          allPosts[index].value = postCellData
        }
      }

      check(postIndexes.size == allPosts.size) {
        "postIndexes.size (${postIndexes.size}) != postsMutable.size (${allPosts.size})"
      }

      if (androidHelpers.isDevFlavor()) {
        val postMutableDeduplicated = allPosts.toHashSetByKey { postCellDataState ->
          postCellDataState.value.postDescriptor
        }

        check(postMutableDeduplicated.size == allPosts.size) {
          "Duplicates found in postsMutable " +
            "postMutableDeduplicated.size=${postMutableDeduplicated.size}, " +
            "postsMutable.size=${allPosts.size})"
        }
      }

      _lastUpdatedOn = SystemClock.elapsedRealtime()
      updatePostListAnimationInfoMap(postCellDataCollection)
      onSearchQueryUpdated(searchQueryFlow.value)
    }
  }

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
    if (allPosts != other.allPosts) return false

    return true
  }

  override fun hashCode(): Int {
    var result = chanDescriptor.hashCode()
    result = 31 * result + allPosts.hashCode()
    return result
  }

}