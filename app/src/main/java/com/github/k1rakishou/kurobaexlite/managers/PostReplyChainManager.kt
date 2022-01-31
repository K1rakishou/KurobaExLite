package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PostReplyChainManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val replyChains = mutableMapOf<ThreadDescriptor, ThreadReplyChain>()

  suspend fun insert(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    val threadReplyChain = mutex.withLock {
      replyChains.getOrPut(
        key = postDescriptor.threadDescriptor,
        defaultValue = { ThreadReplyChain() }
      )
    }

    threadReplyChain.insert(postDescriptor, repliesTo)
  }

  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = mutex.withLock { replyChains[postDescriptor.threadDescriptor] }
      ?: return emptySet()

    return threadReplyChain.getRepliesTo(postDescriptor)
  }

  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = mutex.withLock { replyChains[postDescriptor.threadDescriptor] }
      ?: return emptySet()

    return threadReplyChain.getRepliesFrom(postDescriptor)
  }

}

private class ThreadReplyChain {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val replyToMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)
  @GuardedBy("mutex")
  private val replyFromMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)

  suspend fun insert(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    mutex.withLock {
      replyToMap[postDescriptor] = repliesTo.toMutableSet()

      for (replyToDescriptor in repliesTo) {
        val repliesFrom = replyFromMap.getOrPut(
          key = replyToDescriptor,
          defaultValue = { mutableSetOf() }
        )

        repliesFrom.add(postDescriptor)
      }
    }
  }

  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLock { replyToMap[postDescriptor]?.toSet() ?: emptySet() }
  }

  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLock { replyFromMap[postDescriptor]?.toSet() ?: emptySet() }
  }

}