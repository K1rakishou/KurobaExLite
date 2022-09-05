package com.github.k1rakishou.kurobaexlite.features.posts.shared.state

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.util.fastForEachIndexed
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.util.linkedMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.toHashSetByKey
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.java.KoinJavaComponent.inject

@Stable
class PostsState(
  val chanDescriptor: ChanDescriptor
) {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  private val postCommentApplier: PostCommentApplier by inject(PostCommentApplier::class.java)

  @Volatile private var _lastUpdatedOn: Long = 0
  val lastUpdatedOn: Long
    get() = _lastUpdatedOn

  private val postIndexes = linkedMapWithCap<PostDescriptor, Int>(128)

  private val _posts = mutableStateListOf<PostCellData>()
  val posts: List<PostCellData>
    get() = _posts

  private val _postsMatchedBySearchQuery = LinkedHashSet<PostDescriptor>()
  val postsMatchedBySearchQuery: Set<PostDescriptor>
    get() = _postsMatchedBySearchQuery

  private val _searchQueryUpdatedFlow = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val searchQueryUpdatedFlow: SharedFlow<Unit>
    get() = _searchQueryUpdatedFlow.asSharedFlow()

  val postListAnimationInfoMap = mutableStateMapOf<PostDescriptor, PreviousPostDataInfo>()

  fun postIndexByPostDescriptor(postDescriptor: PostDescriptor): Int? {
    return postIndexes[postDescriptor]
  }

  fun getPosts(postDescriptors: Collection<PostDescriptor>): List<PostCellData> {
    val resultList = mutableListWithCap<PostCellData>(postDescriptors.size)

    postDescriptors.forEach { postDescriptor ->
      val postIndex = postIndexes[postDescriptor]
        ?: return@forEach
      val postCellData = _posts.getOrNull(postIndex)
        ?: return@forEach

      resultList += postCellData
    }

    return resultList
  }

  fun onSearchQueryUpdated(searchQuery: String?) {
    val chanTheme = themeEngine.chanTheme
    val modifyQueryMap = mutableMapOf<PostDescriptor, PostCellData>()
    _postsMatchedBySearchQuery.clear()

    posts.fastForEachIndexed { index, postCellData ->
      val parsedPostData = postCellData.parsedPostData
        ?: return@fastForEachIndexed

      val prevPost = _posts[index]

      val (foundOccurrencesInComment, newProcessedPostComment) = postCommentApplier.markOrUnmarkSearchQuery(
        chanTheme = chanTheme,
        searchQuery = searchQuery,
        minQueryLength = MIN_SEARCH_QUERY_LENGTH,
        string = postCellData.parsedPostData.processedPostComment,
      )

      val (foundOccurrencesInSubject, newProcessedPostSubject) = postCommentApplier.markOrUnmarkSearchQuery(
        chanTheme = chanTheme,
        searchQuery = searchQuery,
        minQueryLength = MIN_SEARCH_QUERY_LENGTH,
        string = postCellData.parsedPostData.processedPostSubject
      )

      if (foundOccurrencesInComment || foundOccurrencesInSubject) {
        _postsMatchedBySearchQuery += postCellData.postDescriptor
      }

      if (
        prevPost.parsedPostData?.processedPostComment == newProcessedPostComment &&
        prevPost.parsedPostData.processedPostSubject == newProcessedPostSubject
      ) {
        return@fastForEachIndexed
      }

      modifyQueryMap[postCellData.postDescriptor] = prevPost.copy(
        parsedPostData = parsedPostData.copy(
          processedPostComment = newProcessedPostComment,
          processedPostSubject = newProcessedPostSubject,
        )
      )
    }

    if (modifyQueryMap.isNotEmpty()) {
      modifyQueryMap.entries.forEach { (postDescriptor, postCellData) ->
        val postIndex = postIndexes[postDescriptor]
          ?: return@forEach

        _posts[postIndex] = postCellData
      }
    }

    _searchQueryUpdatedFlow.tryEmit(Unit)
  }

  /**
   * Do not call directly! Use PostScreenState.insertOrUpdate() instead!
   * */
  fun insertOrUpdate(
    postCellData: PostCellData,
    checkFirstPostIsOriginal: Boolean
  ) {
    val descriptorsMatch = when (chanDescriptor) {
      is CatalogDescriptor -> postCellData.postDescriptor.catalogDescriptor == chanDescriptor
      is ThreadDescriptor -> postCellData.postDescriptor.threadDescriptor == chanDescriptor
    }

    if (!descriptorsMatch) {
      return
    }

    Snapshot.withMutableSnapshot {
      val index = postIndexes[postCellData.postDescriptor]
      if (index == null) {
        val nextPostIndex = postIndexes.values.maxOrNull()?.plus(1) ?: 0
        postIndexes[postCellData.postDescriptor] = nextPostIndex

        // We assume that posts can only be inserted at the end of the post list
        _posts += postCellData
      } else {
        _lastUpdatedOn = SystemClock.elapsedRealtime()
        _posts[index] = postCellData
      }

      if (androidHelpers.isDevFlavor()) {
        checkPostsCorrectness(
          checkFirstPostIsOriginal = checkFirstPostIsOriginal,
          inputPostsCount = 1
        )
      }

      updatePostListAnimationInfoMap(listOf(postCellData))
    }
  }

  /**
   * Do not call directly! Use PostScreenState.insertOrUpdateMany() instead!
   * */
  fun insertOrUpdateMany(
    postCellDataCollection: Collection<PostCellData>,
    checkFirstPostIsOriginal: Boolean
  ) {
    if (postCellDataCollection.isEmpty()) {
      return
    }

    Snapshot.withMutableSnapshot {
      var initialIndex = postIndexes.values.maxOrNull()?.plus(1) ?: 0

      for (postCellData in postCellDataCollection) {
        val postDescriptor = postCellData.postDescriptor

        val descriptorsMatch = when (chanDescriptor) {
          is CatalogDescriptor -> postDescriptor.catalogDescriptor == chanDescriptor
          is ThreadDescriptor -> postDescriptor.threadDescriptor == chanDescriptor
        }

        if (!descriptorsMatch) {
          continue
        }

        val index = postIndexes[postDescriptor]
        if (index == null) {
          postIndexes[postDescriptor] = initialIndex++

          // We assume that posts can only be inserted at the end of the post list
          _posts += postCellData
        } else {
          _posts[index] = postCellData
        }
      }

      if (androidHelpers.isDevFlavor()) {
        checkPostsCorrectness(
          checkFirstPostIsOriginal = checkFirstPostIsOriginal,
          inputPostsCount = postCellDataCollection.size
        )
      }

      _lastUpdatedOn = SystemClock.elapsedRealtime()
      updatePostListAnimationInfoMap(postCellDataCollection)
    }
  }

  private fun checkPostsCorrectness(checkFirstPostIsOriginal: Boolean, inputPostsCount: Int) {
    if (checkFirstPostIsOriginal) {
      val originalPost = _posts.firstOrNull()
      if (originalPost != null) {
        check(originalPost.isOP) { "First post is not OP" }
      }
    }

    check(postIndexes.size == _posts.size) {
      "postIndexes.size (${postIndexes.size}) != postsMutable.size (${_posts.size})"
    }

    val postMutableDeduplicated = _posts.toHashSetByKey { postCellDataState ->
      postCellDataState.postDescriptor
    }

    check(postMutableDeduplicated.size == _posts.size) {
      "Duplicates found in postsMutable " +
        "postMutableDeduplicated.size=${postMutableDeduplicated.size}, " +
        "postsMutable.size=${_posts.size})"
    }

    if (chanDescriptor is ThreadDescriptor) {
      var prevPostDescriptor: PostDescriptor? = null

      for ((index, postCellData) in _posts.withIndex()) {
        val currentPostDescriptor = postCellData.postDescriptor

        if (prevPostDescriptor != null) {
          check(prevPostDescriptor < currentPostDescriptor) {
            "Post incorrect ordering detected at ${index}/${_posts.lastIndex}! " +
              "prevPostDescriptor=${prevPostDescriptor}, " +
              "currentPostDescriptor=${currentPostDescriptor}, " +
              "inputPostsCount=${inputPostsCount}"
          }
        }

        prevPostDescriptor = currentPostDescriptor
      }
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
    if (_posts != other._posts) return false

    return true
  }

  override fun hashCode(): Int {
    var result = chanDescriptor.hashCode()
    result = 31 * result + _posts.hashCode()
    return result
  }

  companion object {
    const val MIN_SEARCH_QUERY_LENGTH = 2
  }

}