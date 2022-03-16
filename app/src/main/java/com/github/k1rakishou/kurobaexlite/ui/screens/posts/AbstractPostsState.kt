package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

abstract class AbstractPostsState(
  val chanDescriptor: ChanDescriptor
) {
  protected val appSettings: AppSettings by inject(AppSettings::class.java)

  @Volatile private var _postsCopy: List<PostData>? = null
  @Volatile private var _lastUpdatedOn: Long = 0

  private val postIndexes = mutableMapOf<PostDescriptor, Int>()

  val lastUpdatedOn: Long
    get() = _lastUpdatedOn

  abstract val posts: List<State<PostData>>
  protected abstract val postsMutable: SnapshotStateList<MutableState<PostData>>

  fun update(postData: PostData) {
    val index = postIndexes[postData.postDescriptor]
      ?: return

    _lastUpdatedOn = SystemClock.elapsedRealtime()
    postsMutable[index].value = postData
  }

  fun updateMany(postDataCollection: Collection<PostData>) {
    if (postDataCollection.isEmpty()) {
      return
    }

    Snapshot.withMutableSnapshot {
      for (postData in postDataCollection) {
        val index = postIndexes[postData.postDescriptor]
          ?: continue

        _lastUpdatedOn = SystemClock.elapsedRealtime()
        postsMutable[index].value = postData
      }
    }
  }

  fun mergePostsWith(newThreadPosts: List<PostData>): PostsMergeResult {
    if (_postsCopy != null) {
      return PostsMergeResult.EMPTY
    }

    val prevThreadPostMap = postsMutable.associateBy { it.value.postDescriptor }
    val postsToUpdate = mutableListOf<PostData>()
    val postsToInsert = mutableListOf<PostData>()
    val now = SystemClock.elapsedRealtime()

    for (newThreadPost in newThreadPosts) {
      val prevThreadPostState = prevThreadPostMap[newThreadPost.postDescriptor]
      if (prevThreadPostState == null) {
        postsToInsert += newThreadPost
        continue
      }

      val prevThreadPost = prevThreadPostState.value
      if (prevThreadPost.differsWith(newThreadPost)) {
        postsToUpdate += newThreadPost
      }
    }

    if (postsToInsert.isNotEmpty() || postsToUpdate.isNotEmpty()) {
      _lastUpdatedOn = now
    }

    Snapshot.withMutableSnapshot {
      if (postsToUpdate.isNotEmpty()) {
        for (postDataToUpdate in postsToUpdate) {
          val index = postIndexes[postDataToUpdate.postDescriptor] ?: continue
          postsMutable[index].value = postDataToUpdate
        }
      }

      if (postsToInsert.isNotEmpty()) {
        val lastIndex = postsMutable.size
        postsMutable.addAll(postsToInsert.map { mutableStateOf(it) })

        for ((index, postToInsert) in postsToInsert.withIndex()) {
          postIndexes[postToInsert.postDescriptor] = lastIndex + index
        }
      }
    }

    val newOrUpdatedPostsToReparse = mutableListWithCap<PostData>(postsToUpdate.size + postsToInsert.size)
    newOrUpdatedPostsToReparse.addAll(postsToUpdate)
    newOrUpdatedPostsToReparse.addAll(postsToInsert)

    logcat { "postsToUpdateCount=${postsToUpdate.size}, postsToInsertCount=${postsToInsert.size}" }

    return PostsMergeResult(
      newPostsCount = postsToInsert.size,
      newOrUpdatedPostsToReparse = newOrUpdatedPostsToReparse
    )
  }

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AbstractPostsState

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