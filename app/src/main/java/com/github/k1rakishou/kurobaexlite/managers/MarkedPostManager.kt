package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.mutableIteration
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPost
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPostType
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MarkedPostManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val markedPostsStorage = mutableMapOf<ThreadDescriptor, MutableSet<MarkedPost>>()

  private val _markedPostsUpdateFlow = MutableSharedFlow<MarkedPostUpdate>(extraBufferCapacity = Channel.UNLIMITED)
  val markedPostsUpdateFlow: SharedFlow<MarkedPostUpdate>
    get() = _markedPostsUpdateFlow.asSharedFlow()

  suspend fun insert(threadDescriptor: ThreadDescriptor, markedPosts: Collection<MarkedPost>) {
    mutex.withLock { markedPostsStorage[threadDescriptor] = markedPosts.toMutableSet() }
    _markedPostsUpdateFlow.emit(MarkedPostUpdate.Loaded(markedPosts))
  }

  suspend fun getMarkedPosts(postDescriptor: PostDescriptor): Set<MarkedPost> {
    return mutex.withLock {
      return@withLock markedPostsStorage[postDescriptor.threadDescriptor]
        ?.filter { markedPost -> markedPost.postDescriptor == postDescriptor }
        ?.toSet()
        ?: emptySet()
    }
  }

  suspend fun getManyMarkedPosts(
    postDescriptorSet: Set<PostDescriptor>
  ): Map<PostDescriptor, Set<MarkedPost>> {
    if (postDescriptorSet.isEmpty()) {
      return emptyMap()
    }

    return mutex.withLock {
      val postDescriptorsGrouped = postDescriptorSet.groupBy { it.threadDescriptor }
      val resultMap = mutableMapWithCap<PostDescriptor, MutableSet<MarkedPost>>(postDescriptorSet.size)

      for ((threadDescriptor, postDescriptors) in postDescriptorsGrouped) {
        val threadMarkedPosts = markedPostsStorage[threadDescriptor]
        if (threadMarkedPosts == null || threadMarkedPosts.isNullOrEmpty()) {
          continue
        }

        for (markedPost in threadMarkedPosts) {
          if (markedPost.postDescriptor in postDescriptors) {
            val markedPosts = resultMap.getOrPut(
              key = markedPost.postDescriptor,
              defaultValue = { mutableSetOf() }
            )

            markedPosts += markedPost
          }
        }
      }

      return@withLock resultMap
    }
  }

  suspend fun isAlreadyLoadedForThread(threadDescriptor: ThreadDescriptor): Boolean {
    return mutex.withLock { markedPostsStorage.containsKey(threadDescriptor) }
  }

  suspend fun isPostMarkedAsMine(postDescriptor: PostDescriptor): Boolean {
    return mutex.withLock {
      markedPostsStorage[postDescriptor.threadDescriptor]
        ?.firstOrNull { markedPost -> markedPost.postDescriptor == postDescriptor }
        ?.markedPostType == MarkedPostType.MyPost
    }
  }

  suspend fun markPost(postDescriptor: PostDescriptor, markedPostType: MarkedPostType): Boolean {
    val markedPost = MarkedPost(postDescriptor, markedPostType)

    val marked = mutex.withLock {
      val threadMarkedPosts = markedPostsStorage.getOrPut(
        key = postDescriptor.threadDescriptor,
        defaultValue = { mutableSetOf() }
      )

      val alreadyExisting = threadMarkedPosts.firstOrNull { markedPost -> markedPost.postDescriptor == postDescriptor }
      if (alreadyExisting != null && alreadyExisting.markedPostType == markedPostType) {
        return@withLock false
      }

      threadMarkedPosts += markedPost
      return@withLock true
    }

    if (marked) {
      _markedPostsUpdateFlow.emit(MarkedPostUpdate.Marked(markedPost))
    }

    return marked
  }

  suspend fun unmarkPost(postDescriptor: PostDescriptor, markedPostType: MarkedPostType): Boolean {
    val unmarked = mutex.withLock {
      val threadMarkedPosts = markedPostsStorage[postDescriptor.threadDescriptor]
        ?: return@withLock false

      var deleted = false

      threadMarkedPosts.mutableIteration { mutableIterator, markedPost ->
        if (markedPost.postDescriptor == postDescriptor && markedPost.markedPostType == markedPostType) {
          mutableIterator.remove()
          deleted = true
          return@mutableIteration false
        }

        return@mutableIteration true
      }

      return@withLock deleted
    }

    if (unmarked) {
      _markedPostsUpdateFlow.emit(MarkedPostUpdate.Unmarked(MarkedPost(postDescriptor, markedPostType)))
    }

    return unmarked
  }

  sealed class MarkedPostUpdate {
    data class Loaded(val markedPosts: Collection<MarkedPost>) : MarkedPostUpdate()
    data class Marked(val markedPost: MarkedPost) : MarkedPostUpdate()
    data class Unmarked(val markedPost: MarkedPost) : MarkedPostUpdate()
  }

}