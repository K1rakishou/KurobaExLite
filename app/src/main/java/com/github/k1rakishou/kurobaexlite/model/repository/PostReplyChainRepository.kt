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

  override suspend fun insert(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    val threadReplyChain = replyChains.getOrPut(
      key = postDescriptor.threadDescriptor,
      defaultValue = { ThreadReplyChain() }
    )

    threadReplyChain.insert(postDescriptor, repliesTo)
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