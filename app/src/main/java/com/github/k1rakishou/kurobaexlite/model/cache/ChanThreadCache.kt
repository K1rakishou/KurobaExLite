package com.github.k1rakishou.kurobaexlite.model.cache

import android.os.SystemClock
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.PostDiffer
import com.github.k1rakishou.kurobaexlite.helpers.util.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.util.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex
import logcat.logcat
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ChanThreadCache(
  val threadDescriptor: ThreadDescriptor
) : IChanCache {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val posts = mutableListOf<IPostData>()
  @GuardedBy("mutex")
  private val postsMap = mutableMapOf<PostDescriptor, IPostData>()

  @Volatile
  override var lastUpdateTime: Long = SystemClock.elapsedRealtime()
    private set

  @Volatile
  var lastFullUpdateTime: Long = 0L
    private set

  override val chanDescriptor: ChanDescriptor = threadDescriptor

  override suspend fun hasPosts(): Boolean {
    return mutex.withLockNonCancellable { posts.isNotEmpty() }
  }

  suspend fun onThreadAccessed() {
    mutex.withLockNonCancellable { lastUpdateTime = SystemClock.elapsedRealtime() }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun insert(postCellDataCollection: Collection<IPostData>, isIncrementalUpdate: Boolean): PostsLoadResult {
    BackgroundUtils.ensureBackgroundThread()

    return mutex.withLockNonCancellable {
      val insertedPosts = mutableListOf<IPostData>()
      val updatedPosts = mutableListOf<IPostData>()
      val unchangedPosts = mutableListOf<IPostData>()
      val deletedPosts = mutableListOf<IPostData>()
      var needSorting = false

      val allPostDescriptorsFromServer = postCellDataCollection.associateBy { it.postDescriptor }

      val duration = measureTime {
        postCellDataCollection.forEach { postData ->
          val prevPostData = postsMap[postData.postDescriptor]

          if (prevPostData != null && PostDiffer.postsDiffer(postData, prevPostData)) {
            val index = posts.indexOfFirst { oldPostData ->
              oldPostData.postDescriptor == postData.postDescriptor
            }
            check(index >= 0) { "postMap contains this post but posts list does not!" }

            val postDataWithPostIndex = postData.copy(originalPostOrder = posts[index].originalPostOrder)
            posts[index] = postDataWithPostIndex
            postsMap[postDataWithPostIndex.postDescriptor] = postDataWithPostIndex

            updatedPosts += postDataWithPostIndex
          } else if (prevPostData == null) {
            val prevPostIndex = posts.lastOrNull()?.originalPostOrder ?: 0
            val postDataWithPostIndex = postData.copy(originalPostOrder = prevPostIndex + 1)

            posts.add(postDataWithPostIndex)
            postsMap[postDataWithPostIndex.postDescriptor] = postDataWithPostIndex

            insertedPosts += postDataWithPostIndex
            needSorting = true
          } else {
            unchangedPosts += postData
          }
        }

        if (!isIncrementalUpdate) {
          posts.forEach { postData ->
            if (!allPostDescriptorsFromServer.containsKey(postData.postDescriptor)) {
              deletedPosts += postData
            }
          }
        }

        if (needSorting) {
          posts.sortWith(POSTS_COMPARATOR)
        }
      }

      logcat(tag = TAG) {
        "insert(isIncrementalUpdate: ${isIncrementalUpdate}) " +
          "insertedPosts=${insertedPosts.size}, " +
          "updatedPosts=${updatedPosts.size}, " +
          "unchangedPosts=${unchangedPosts.size}, " +
          "deletedPosts=${deletedPosts.size}, " +
          "totalFromServer=${postCellDataCollection.size}, " +
          "totalCached=${postsMap.size}, " +
          "insertion took: ${duration}"
      }

      return@withLockNonCancellable PostsLoadResult(
        chanDescriptor = threadDescriptor,
        newPosts = insertedPosts,
        updatedPosts = updatedPosts,
        unchangedPosts = unchangedPosts,
        deletedPosts = deletedPosts
      )
    }
  }

  suspend fun getMany(postDescriptors: Collection<PostDescriptor>): List<IPostData> {
    return mutex.withLockNonCancellable {
      postDescriptors.mapNotNull { postDescriptor -> postsMap[postDescriptor] }
    }
  }

  suspend fun getPostDescriptorList(): List<PostDescriptor> {
    return mutex.withLockNonCancellable { posts.map { post -> post.postDescriptor } }
  }

  suspend fun getAll(): List<IPostData> {
    return mutex.withLockNonCancellable { posts.toList() }
  }

  suspend fun getOriginalPost(): OriginalPostData? {
    return mutex.withLockNonCancellable {
      val originalPostMaybe = posts.firstOrNull()
        ?: return@withLockNonCancellable null

      check(originalPostMaybe is OriginalPostData) { "originalPostMaybe is not OriginalPostData" }

      return@withLockNonCancellable originalPostMaybe
    }
  }

  suspend fun getPost(postDescriptor: PostDescriptor): IPostData? {
    return mutex.withLockNonCancellable { postsMap[postDescriptor] }
  }

  suspend fun getLastPost(): IPostData? {
    return mutex.withLockNonCancellable { posts.lastOrNull() }
  }

  suspend fun resetLastFullUpdateTime() {
    mutex.withLockNonCancellable {
      lastFullUpdateTime = 0L
    }
  }

  suspend fun getLastLoadedPostForIncrementalUpdate(): IPostData? {
    return mutex.withLockNonCancellable {
      val currentTime = SystemClock.elapsedRealtime()

      // Once in FULL_UPDATE_INTERVAL_MS make full thread updates. We need this to keep track of deleted posts.
      if (currentTime - lastFullUpdateTime > FULL_UPDATE_INTERVAL_MS) {
        lastFullUpdateTime = currentTime
        return@withLockNonCancellable null
      }

      // Threads must have more than one post since the very first post is always the OP and we load that post from the
      // catalog thread list which doesn't mean we have visited that thread yet
      if (posts.size <= 1) {
        return@withLockNonCancellable null
      }

      return@withLockNonCancellable posts.lastOrNull()
    }
  }

  suspend fun getNewPostsCount(postDescriptor: PostDescriptor): Int {
    return mutex.withLockNonCancellable {
      posts.count { chanPost -> chanPost.postDescriptor > postDescriptor }
    }
  }

  companion object {
    private const val TAG = "ChanThreadCache"

    private val POSTS_COMPARATOR = compareBy<IPostData> { postData -> postData.postDescriptor }
    private val FULL_UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)
  }

}