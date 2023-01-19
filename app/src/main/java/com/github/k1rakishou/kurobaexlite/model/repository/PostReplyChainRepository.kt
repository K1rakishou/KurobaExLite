package com.github.k1rakishou.kurobaexlite.model.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

class PostReplyChainRepository : IPostReplyChainRepository {
  private val replyChains = ConcurrentHashMap<ThreadDescriptor, ThreadReplyChain>()

  override suspend fun copyThreadReplyChain(threadDescriptor: ThreadDescriptor): ThreadReplyChainCopy? {
    return replyChains[threadDescriptor]?.toThreadReplyChainCopy()
  }

  override suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    val threadReplyChain = replyChains.getOrPut(
      key = postDescriptor.threadDescriptor,
      defaultValue = { ThreadReplyChainActual() }
    )

    threadReplyChain.insertRepliesTo(postDescriptor, repliesTo)
  }

  override suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>) {
    val threadReplyChain = replyChains.getOrPut(
      key = postDescriptor.threadDescriptor,
      defaultValue = { ThreadReplyChainActual() }
    )

    threadReplyChain.insertRepliesFrom(postDescriptor, repliesFrom)
  }

  override suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = replyChains[postDescriptor.threadDescriptor]
      ?: return emptySet()

    return threadReplyChain.getRepliesTo(postDescriptor)
  }

  override suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = replyChains[postDescriptor.threadDescriptor]
      ?: return emptySet()

    return threadReplyChain.getRepliesFrom(postDescriptor)
  }

  override suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = replyChains[postDescriptor.threadDescriptor]
      ?: return emptySet()

    return threadReplyChain.getAllRepliesFromRecursively(postDescriptor)
  }

  override suspend fun getManyRepliesTo(postDescriptors: List<PostDescriptor>): Set<PostDescriptor> {
    val mapped = postDescriptors
      .mapNotNull { postDescriptor ->
        val threadReplyChain = replyChains[postDescriptor.threadDescriptor]
          ?: return@mapNotNull null

        return@mapNotNull postDescriptor to threadReplyChain
      }

    if (mapped.isEmpty()) {
      return emptySet()
    }

    val threadReplyChainMap = mutableMapWithCap<PostDescriptor, ThreadReplyChain>(mapped.size)

    for ((postDescriptor, threadReplyChain) in mapped) {
      threadReplyChainMap[postDescriptor] = threadReplyChain
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

  override suspend fun findPostWithRepliesRecursive(
    postDescriptor: PostDescriptor,
    includeRepliesFrom: Boolean,
    includeRepliesTo: Boolean,
    maxRecursion: Int,
    resultPostDescriptors: MutableSet<PostDescriptor>
  ) {
    require(includeRepliesFrom || includeRepliesTo) {
      "Either includeRepliesFrom or includeRepliesTo must be true"
    }

    if (maxRecursion < 0) {
      return
    }

    val threadReplyChain = replyChains[postDescriptor.threadDescriptor]
      ?: return

    if (includeRepliesFrom) {
      threadReplyChain.getRepliesFrom(postDescriptor).forEach { replyFrom ->
        if (!resultPostDescriptors.add(replyFrom)) {
          return@forEach
        }

        findPostWithRepliesRecursive(
          postDescriptor = replyFrom,
          includeRepliesFrom = true,
          includeRepliesTo = includeRepliesTo,
          maxRecursion = maxRecursion - 1,
          resultPostDescriptors = resultPostDescriptors
        )
      }
    }

    if (includeRepliesTo) {
      threadReplyChain.getRepliesTo(postDescriptor).forEach { replyTo ->
        if (!resultPostDescriptors.add(replyTo)) {
          return@forEach
        }

        findPostWithRepliesRecursive(
          postDescriptor = replyTo,
          includeRepliesFrom = includeRepliesFrom,
          includeRepliesTo = true,
          maxRecursion = maxRecursion - 1,
          resultPostDescriptors = resultPostDescriptors
        )
      }
    }
  }

}

interface ThreadReplyChain {
  suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>)
  suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>)
  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun toThreadReplyChainCopy(): ThreadReplyChainCopy
}

class ThreadReplyChainCopy(
  replyToMap: Map<PostDescriptor, MutableSet<PostDescriptor>>,
  replyFromMap: Map<PostDescriptor, MutableSet<PostDescriptor>>,
) : ThreadReplyChain {
  private val replyToMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)
  private val replyFromMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)

  init {
    this.replyToMap.putAll(replyToMap)
    this.replyFromMap.putAll(replyFromMap)
  }

  override suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    replyToMap[postDescriptor] = repliesTo.toMutableSet()

    for (replyToDescriptor in repliesTo) {
      val repliesFrom = replyFromMap.getOrPut(
        key = replyToDescriptor,
        defaultValue = { mutableSetOf() }
      )

      repliesFrom.add(postDescriptor)
    }
  }

  override suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>) {
    replyFromMap[postDescriptor] = repliesFrom.toMutableSet()

    for (replyFromDescriptor in repliesFrom) {
      val repliesTo = replyToMap.getOrPut(
        key = replyFromDescriptor,
        defaultValue = { mutableSetOf() }
      )

      repliesTo.add(postDescriptor)
    }
  }

  override suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return replyToMap[postDescriptor]?.toSet() ?: emptySet()
  }

  override suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return replyFromMap[postDescriptor]?.toSet() ?: emptySet()
  }

  override suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val collectedReplies = linkedSetOf<PostDescriptor>()
    getAllRepliesFromRecursivelyInternal(postDescriptor, collectedReplies)
    return collectedReplies
  }

  override suspend fun toThreadReplyChainCopy(): ThreadReplyChainCopy {
    return this
  }

  private fun getAllRepliesFromRecursivelyInternal(
    postDescriptor: PostDescriptor,
    collectedReplies: MutableSet<PostDescriptor>
  ) {
    val replies = replyFromMap[postDescriptor]
      ?: return

    for (reply in replies) {
      if (!collectedReplies.add(reply)) {
        continue
      }

      getAllRepliesFromRecursivelyInternal(reply, collectedReplies)
    }
  }
}

private class ThreadReplyChainActual : ThreadReplyChain {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val replyToMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)
  @GuardedBy("mutex")
  private val replyFromMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)

  override suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
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

  override suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>) {
    mutex.withLockNonCancellable {
      replyFromMap[postDescriptor] = repliesFrom.toMutableSet()

      for (replyFromDescriptor in repliesFrom) {
        val repliesTo = replyToMap.getOrPut(
          key = replyFromDescriptor,
          defaultValue = { mutableSetOf() }
        )

        repliesTo.add(postDescriptor)
      }
    }
  }

  override suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLockNonCancellable { replyToMap[postDescriptor]?.toSet() ?: emptySet() }
  }

  override suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLockNonCancellable { replyFromMap[postDescriptor]?.toSet() ?: emptySet() }
  }

  override suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLockNonCancellable {
      val collectedReplies = linkedSetOf<PostDescriptor>()
      getAllRepliesFromRecursivelyInternal(postDescriptor, collectedReplies)
      return@withLockNonCancellable collectedReplies
    }
  }

  override suspend fun toThreadReplyChainCopy(): ThreadReplyChainCopy {
    return ThreadReplyChainCopy(
      replyToMap = replyToMap.toMap(),
      replyFromMap = replyFromMap.toMap()
    )
  }

  private fun getAllRepliesFromRecursivelyInternal(
    postDescriptor: PostDescriptor,
    collectedReplies: MutableSet<PostDescriptor>
  ) {
    val replies = replyFromMap[postDescriptor]
      ?: return

    for (reply in replies) {
      if (!collectedReplies.add(reply)) {
        continue
      }

      getAllRepliesFromRecursivelyInternal(reply, collectedReplies)
    }
  }

}