package com.github.k1rakishou.kurobaexlite.model.cache

import android.os.SystemClock
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.PostDiffer
import com.github.k1rakishou.kurobaexlite.helpers.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
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
  private val posts = mutableListOf<IPostData>()
  @GuardedBy("mutex")
  private val postsMap = mutableMapOf<PostDescriptor, IPostData>()

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

  suspend fun insert(postCellDataCollection: Collection<IPostData>): PostsLoadResult {
    return mutex.withLockNonCancellable {
      val insertedPosts = mutableListOf<IPostData>()
      val updatedPosts = mutableListOf<IPostData>()
      var needSorting = false

      postCellDataCollection.forEach { postData ->
        val prevPostData = postsMap[postData.postDescriptor]

        if (prevPostData != null) {
          if (PostDiffer.postsDiffer(postData, prevPostData)) {
            val index = posts.indexOfFirst { oldPostData ->
              oldPostData.postDescriptor == postData.postDescriptor
            }
            check(index >= 0) { "postMap contains this post but posts list does not!" }

            posts[index] = postData
            postsMap[postData.postDescriptor] = postData

            updatedPosts += postData
          }
        } else {
          posts.add(postData)
          postsMap[postData.postDescriptor] = postData

          insertedPosts += postData
          needSorting = true
        }
      }

      if (needSorting) {
        posts.sortWith(POSTS_COMPARATOR)
      }

      logcat(tag = TAG) {
        "insert() insertedPosts=${insertedPosts.size}, " +
          "updatedPosts=${updatedPosts.size} " +
          "out of ${postCellDataCollection.size}"
      }

      return@withLockNonCancellable PostsLoadResult(
        newPosts = insertedPosts,
        updatedPosts = updatedPosts
      )
    }
  }

  suspend fun getMany(postDescriptors: Collection<PostDescriptor>): List<IPostData> {
    return mutex.withLockNonCancellable {
      postDescriptors.mapNotNull { postDescriptor -> postsMap[postDescriptor] }
    }
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

  companion object {
    private const val TAG = "ChanThread"

    private val POSTS_SUB_NO_COMPARATOR = compareBy<IPostData> { it.postSubNo }
    private val POSTS_COMPARATOR = compareBy<IPostData> { it.postNo }.then(POSTS_SUB_NO_COMPARATOR)
  }

}