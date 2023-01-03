package com.github.k1rakishou.kurobaexlite.model.repository

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

interface IPostReplyChainRepository {
  suspend fun insert(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>)
  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getManyRepliesTo(postDescriptors: List<PostDescriptor>): Set<PostDescriptor>

  suspend fun findPostWithRepliesRecursive(
    postDescriptor: PostDescriptor,
    includeRepliesFrom: Boolean,
    includeRepliesTo: Boolean,
    maxRecursion: Int = Int.MAX_VALUE,
    resultPostDescriptors: MutableSet<PostDescriptor> = mutableSetOf()
  )
}