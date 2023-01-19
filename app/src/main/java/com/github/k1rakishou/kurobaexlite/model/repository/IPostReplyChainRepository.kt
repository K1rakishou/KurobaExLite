package com.github.k1rakishou.kurobaexlite.model.repository

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

interface IPostReplyChainRepository {
  suspend fun copyThreadReplyChain(threadDescriptor: ThreadDescriptor): ThreadReplyChainCopy?

  suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>)
  suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>)

  /**
   * Get descriptors of posts **this post replies to**
   * */
  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor>

  /**
   * Get descriptors of posts that **reply to this post**
   * */
  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getManyRepliesTo(postDescriptors: List<PostDescriptor>): Set<PostDescriptor>

  suspend fun findPostWithRepliesRecursive(
    postDescriptor: PostDescriptor,
    includeRepliesFrom: Boolean,
    includeRepliesTo: Boolean,
    maxRecursion: Int = Int.MAX_VALUE,
    resultPostDescriptors: MutableSet<PostDescriptor> = mutableSetOf()
  )
}