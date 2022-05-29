package com.github.k1rakishou.kurobaexlite.model.cache

import android.os.SystemClock
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
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
    BackgroundUtils.ensureBackgroundThread()

    return mutex.withLockNonCancellable {
      val insertedPosts = mutableListOf<IPostData>()
      val updatedPosts = mutableListOf<IPostData>()
      val unchangedPosts = mutableListOf<IPostData>()
      val deletedPosts = mutableListOf<IPostData>()
      var needSorting = false

      val allPostDescriptorsFromServer = postCellDataCollection.associateBy { it.postDescriptor }

      postCellDataCollection.forEach { postData ->
        val prevPostData = postsMap[postData.postDescriptor]

        if (prevPostData != null && PostDiffer.postsDiffer(postData, prevPostData)) {
          val index = posts.indexOfFirst { oldPostData ->
            oldPostData.postDescriptor == postData.postDescriptor
          }
          check(index >= 0) { "postMap contains this post but posts list does not!" }

          posts[index] = postData
          postsMap[postData.postDescriptor] = postData

          updatedPosts += postData
        } else if (prevPostData == null) {
          posts.add(postData)
          postsMap[postData.postDescriptor] = postData

          insertedPosts += postData
          needSorting = true
        } else {
          unchangedPosts += postData
        }
      }

      posts.forEach { postData ->
        if (!allPostDescriptorsFromServer.containsKey(postData.postDescriptor)) {
          deletedPosts += postData
        }
      }

      if (needSorting) {
        posts.sortWith(POSTS_COMPARATOR)
      }

      logcat(tag = TAG) {
        "insert() insertedPosts=${insertedPosts.size}, " +
          "updatedPosts=${updatedPosts.size}, " +
          "unchangedPosts=${unchangedPosts.size}, " +
          "totalFromServer=${postCellDataCollection.size}, " +
          "totalCached=${postsMap.size}"
      }

      return@withLockNonCancellable PostsLoadResult(
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

  suspend fun getNewPostsCount(postDescriptor: PostDescriptor): Int {
    return mutex.withLockNonCancellable {
      posts.count { chanPost -> chanPost.postDescriptor > postDescriptor }
    }
  }

  companion object {
    private const val TAG = "ChanThreadCache"

    private val POSTS_COMPARATOR = compareBy<IPostData> { postData -> postData.postDescriptor }
  }

}