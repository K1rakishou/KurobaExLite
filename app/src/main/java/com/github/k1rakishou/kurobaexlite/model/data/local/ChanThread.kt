package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat

class ChanThread {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val posts = mutableListOf<PostData>()
  @GuardedBy("mutex")
  private val postsMap = mutableMapOf<PostDescriptor, PostData>()

  suspend fun insert(postDataCollection: Collection<PostData>) {
    mutex.withLock {
      var insertedPosts = 0
      var updatedPosts = 0
      var needSorting = false

      postDataCollection.forEach { postData ->
        if (postData.parsedPostDataRead == null) {
          error("parsedPostDataRead is null")
        }

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
    return mutex.withLock { postDescriptors.mapNotNull { postDescriptor -> postsMap[postDescriptor] } }
  }

  suspend fun getAll(): List<PostData> {
    return mutex.withLock { posts.toList() }
  }

  suspend fun getOriginalPost(): OriginalPostData? {
    return mutex.withLock {
      val originalPostMaybe = posts.firstOrNull()
        ?: return@withLock null

      check(originalPostMaybe is OriginalPostData) { "originalPostMaybe is not OriginalPostData" }

      return@withLock originalPostMaybe
    }
  }

  suspend fun getPost(postDescriptor: PostDescriptor): PostData? {
    return mutex.withLock { postsMap[postDescriptor] }
  }

  suspend fun getLastPost(): PostData? {
    return mutex.withLock { posts.lastOrNull() }
  }

  companion object {
    private const val TAG = "ChanThread"

    private val POSTS_SUB_NO_COMPARATOR = compareBy<PostData> { it.postSubNo }
    private val POSTS_COMPARATOR = compareBy<PostData> { it.postNo }.then(POSTS_SUB_NO_COMPARATOR)
  }

}