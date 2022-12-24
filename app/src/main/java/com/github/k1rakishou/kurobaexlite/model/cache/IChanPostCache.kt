package com.github.k1rakishou.kurobaexlite.model.cache

import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.flow.Flow

interface IChanPostCache {
  suspend fun getCatalogPost(postDescriptor: PostDescriptor): OriginalPostData?
  suspend fun getThreadPost(postDescriptor: PostDescriptor): IPostData?
  suspend fun getNewPostsCount(postDescriptor: PostDescriptor): Int
  suspend fun getCatalogThreads(catalogDescriptor: CatalogDescriptor): List<OriginalPostData>
  suspend fun getOriginalPost(threadDescriptor: ThreadDescriptor): OriginalPostData?
  suspend fun getThreadPosts(threadDescriptor: ThreadDescriptor): List<IPostData>
  suspend fun delete(chanDescriptor: ChanDescriptor)
  suspend fun insertCatalogThreads(catalogDescriptor: CatalogDescriptor, catalogThreads: Collection<IPostData>): PostsLoadResult
  suspend fun getLastLoadedPostForIncrementalUpdate(threadDescriptor: ThreadDescriptor): IPostData?
  suspend fun insertThreadPosts(
    threadDescriptor: ThreadDescriptor,
    threadPostCells: Collection<IPostData>,
    isIncrementalUpdate: Boolean
  ): PostsLoadResult

  suspend fun resetThreadLastFullUpdateTime(threadDescriptor: ThreadDescriptor)
  fun listenForPostUpdates(chanDescriptor: ChanDescriptor): Flow<PostsLoadResult>
}