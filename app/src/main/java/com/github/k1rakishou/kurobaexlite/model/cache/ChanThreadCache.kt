package com.github.k1rakishou.kurobaexlite.model.cache

import android.os.SystemClock
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex
import logcat.logcat

class ChanThreadCache(
  val threadDescriptor: ThreadDescriptor
) : IChanCache {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val posts = mutableListOf<PostData>()
  @GuardedBy("mutex")
  private val postsMap = mutableMapOf<PostDescriptor, PostData>()

  @Volatile
  override var lastUpdateTime: Long = SystemClock.elapsedRealtime()
    private set

  override val chanDescriptor: ChanDescriptor = threadDescriptor

  override suspend fun hasPosts(): Boolean {
    return mutex.withLockNonCancellable { posts.isNotEmpty() }
  }

  suspend fun onThreadAccessed() {
    mutex.withLockNonCancellable { lastUpdateTime = SystemClock.elapsedRealtime() }
  }

  suspend fun insert(postDataCollection: Collection<PostData>) {
    mutex.withLockNonCancellable {
      var insertedPosts = 0
      var updatedPosts = 0
      var needSorting = false

      postDataCollection.forEach { postData ->
        if (postsMap.contains(postData.postDescriptor)) {
          val index = posts.indexOfFirst { oldPostData -> oldPostData.postDescriptor == postData.postDescriptor }
          check(index >= 0) { "postMap contains this post but posts list does not!" }

          posts[index] = postData
          postsMap[postData.postDescriptor] = postData

          ++updatedPosts
        } else {
          posts.add(postData)
          postsMap[postData.postDescriptor] = postData

          ++insertedPosts
          needSorting = true
        }
      }

      if (needSorting) {
        posts.sortWith(POSTS_COMPARATOR)
      }

      logcat(tag = TAG) { "insert() insertedPosts=$insertedPosts, updatedPosts=$updatedPosts" }
    }
  }

  suspend fun getMany(postDescriptors: Collection<PostDescriptor>): List<PostData> {
    return mutex.withLockNonCancellable { postDescriptors.mapNotNull { postDescriptor -> postsMap[postDescriptor] } }
  }

  suspend fun getAll(): List<PostData> {
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

  suspend fun getPost(postDescriptor: PostDescriptor): PostData? {
    return mutex.withLockNonCancellable { postsMap[postDescriptor] }
  }

  suspend fun getLastPost(): PostData? {
    return mutex.withLockNonCancellable { posts.lastOrNull() }
  }

  companion object {
    private const val TAG = "ChanThread"

    private val POSTS_SUB_NO_COMPARATOR = compareBy<PostData> { it.postSubNo }
    private val POSTS_COMPARATOR = compareBy<PostData> { it.postNo }.then(POSTS_SUB_NO_COMPARATOR)
  }

}