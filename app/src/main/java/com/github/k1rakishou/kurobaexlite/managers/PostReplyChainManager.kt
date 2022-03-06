package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.helpers.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex

class PostReplyChainManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val replyChains = mutableMapOf<ThreadDescriptor, ThreadReplyChain>()

  suspend fun insert(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    val threadReplyChain = mutex.withLockNonCancellable {
      replyChains.getOrPut(
        key = postDescriptor.threadDescriptor,
        defaultValue = { ThreadReplyChain() }
      )
    }

    threadReplyChain.insert(postDescriptor, repliesTo)
  }

  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = mutex.withLockNonCancellable { replyChains[postDescriptor.threadDescriptor] }
      ?: return emptySet()

    return threadReplyChain.getRepliesTo(postDescriptor)
  }

  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = mutex.withLockNonCancellable { replyChains[postDescriptor.threadDescriptor] }
      ?: return emptySet()

    return threadReplyChain.getRepliesFrom(postDescriptor)
  }

  suspend fun getManyRepliesTo(postDescriptors: List<PostDescriptor>): Set<PostDescriptor> {
    val threadReplyChainMap = mutex.withLockNonCancellable {
      val mapped = postDescriptors
        .mapNotNull { postDescriptor ->
          val threadReplyChain = replyChains[postDescriptor.threadDescriptor]
            ?: return@mapNotNull null

          return@mapNotNull postDescriptor to threadReplyChain
        }

      if (mapped.isEmpty()) {
        return@withLockNonCancellable emptyMap()
      }

      val resultMap = mutableMapWithCap<PostDescriptor, ThreadReplyChain>(mapped.size)

      for ((postDescriptor, threadReplyChain) in mapped) {
        resultMap[postDescriptor] = threadReplyChain
      }

      return@withLockNonCancellable resultMap
    }

    if (threadReplyChainMap.isEmpty()) {
      return emptySet()
    }

    val resultSet = mutableSetWithCap<PostDescriptor>(threadReplyChainMap.size * 2)

    for ((postDescriptor, threadReplyChain) in threadReplyChainMap) {
      val repliesTo = threadReplyChain.getRepliesTo(postDescriptor)
      resultSet.addAll(repliesTo)
    }

    return resultSet
  }

}

private class ThreadReplyChain {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val replyToMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)
  @GuardedBy("mutex")
  private val replyFromMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)

  suspend fun insert(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    mutex.withLockNonCancellable {
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
    return mutex.withLockNonCancellable { replyToMap[postDescriptor]?.toSet() ?: emptySet() }
  }

  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLockNonCancellable { replyFromMap[postDescriptor]?.toSet() ?: emptySet() }
  }

}