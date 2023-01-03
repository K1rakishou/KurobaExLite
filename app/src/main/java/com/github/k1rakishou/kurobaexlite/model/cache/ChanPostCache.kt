package com.github.k1rakishou.kurobaexlite.model.cache

import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableIteration
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap

class ChanPostCache(
  private val androidHelpers: AndroidHelpers
) : IChanPostCache {
  private val maxCachedCatalogs by lazy { if (androidHelpers.isDevFlavor()) 3 else 5 }
  private val maxCachedThreads by lazy { if (androidHelpers.isDevFlavor()) 5 else 30 }

  private val catalogs = ConcurrentHashMap<CatalogDescriptor, ChanCatalogCache>()
  private val threads = ConcurrentHashMap<ThreadDescriptor, ChanThreadCache>()

  private val _threadPostUpdates = MutableSharedFlow<PostsLoadResult>(extraBufferCapacity = Channel.UNLIMITED)
  private val _catalogThreadUpdates = MutableSharedFlow<PostsLoadResult>(extraBufferCapacity = Channel.UNLIMITED)

  override fun listenForPostUpdates(chanDescriptor: ChanDescriptor): Flow<PostsLoadResult> {
    return when (chanDescriptor) {
      is CatalogDescriptor -> {
        _catalogThreadUpdates
          .filter { postsLoadResult -> postsLoadResult.chanDescriptor == chanDescriptor }
      }
      is ThreadDescriptor -> {
        _threadPostUpdates
          .filter { postsLoadResult -> postsLoadResult.chanDescriptor == chanDescriptor }
      }
    }
  }

  override suspend fun resetThreadLastFullUpdateTime(threadDescriptor: ThreadDescriptor) {
    threads[threadDescriptor]?.resetLastFullUpdateTime()
  }

  override suspend fun onCatalogOrThreadAccessed(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is CatalogDescriptor -> catalogs[chanDescriptor]?.onCatalogAccessed()
      is ThreadDescriptor -> threads[chanDescriptor]?.onThreadAccessed()
    }
  }

  override suspend fun insertCatalogThreads(
    catalogDescriptor: CatalogDescriptor,
    catalogThreads: Collection<IPostData>
  ): PostsLoadResult {
    return withContext(Dispatchers.IO) {
      val chanCatalog = catalogs.getOrPut(
        key = catalogDescriptor,
        defaultValue = { ChanCatalogCache(catalogDescriptor) }
      )

      val hasPosts = chanCatalog.hasPosts()
      chanCatalog.insert(catalogThreads)

      // Only run evictOld() routine when inserting new catalogs into the cache
      if (!hasPosts) {
        evictOld(catalogs as ConcurrentHashMap<ChanDescriptor, IChanCache>, maxCachedCatalogs)
      }

      val postsLoadResult = PostsLoadResult(
        chanDescriptor = catalogDescriptor,
        newPosts = catalogThreads.toList(),
        updatedPosts = emptyList()
      )

      _catalogThreadUpdates.emit(postsLoadResult)

      return@withContext postsLoadResult
    }
  }

  override suspend fun insertThreadPosts(
    threadDescriptor: ThreadDescriptor,
    threadPostCells: Collection<IPostData>,
    isIncrementalUpdate: Boolean
  ): PostsLoadResult {
    return withContext(Dispatchers.IO) {
      val chanThread = threads.getOrPut(
        key = threadDescriptor,
        defaultValue = { ChanThreadCache(threadDescriptor) }
      )

      val hasPosts = chanThread.hasPosts()
      val postsMergeResult = chanThread.insert(threadPostCells, isIncrementalUpdate)

      // Only run evictOld() routine when inserting new threads into the cache
      if (!hasPosts) {
        evictOld(threads as ConcurrentHashMap<ChanDescriptor, IChanCache>, maxCachedThreads)
      }

      _threadPostUpdates.emit(postsMergeResult)

      return@withContext postsMergeResult
    }
  }

  suspend fun getPostDescriptors(chanDescriptor: ChanDescriptor): List<PostDescriptor> {
    return when (chanDescriptor) {
      is CatalogDescriptor -> catalogs[chanDescriptor]?.getPostDescriptorList() ?: emptyList()
      is ThreadDescriptor -> threads[chanDescriptor]?.getPostDescriptorList() ?: emptyList()
    }
  }

  override suspend fun delete(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is CatalogDescriptor -> catalogs.remove(chanDescriptor)
      is ThreadDescriptor -> threads.remove(chanDescriptor)
    }
  }

  override suspend fun getManyForDescriptor(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): List<IPostData> {
    val resultList = mutableListWithCap<IPostData>(postDescriptors.size)

    when (chanDescriptor) {
      is CatalogDescriptor -> {
        val catalogThreads = catalogs[chanDescriptor]?.getMany(postDescriptors)
        if (catalogThreads.isNotNullNorEmpty()) {
          resultList.addAll(catalogThreads)
        }
      }
      is ThreadDescriptor -> {
        val threadPosts = threads[chanDescriptor]?.getMany(postDescriptors)
        if (threadPosts.isNotNullNorEmpty()) {
          resultList.addAll(threadPosts)
        }
      }
    }

    return resultList
  }

  override suspend fun getThreadPosts(threadDescriptor: ThreadDescriptor): List<IPostData> {
    return threads[threadDescriptor]?.getAll() ?: emptyList()
  }

  override suspend fun getCatalogThreads(catalogDescriptor: CatalogDescriptor): List<OriginalPostData> {
    return catalogs[catalogDescriptor]?.getPostDataList() ?: emptyList()
  }

  override suspend fun getCatalogPost(postDescriptor: PostDescriptor): OriginalPostData? {
    return catalogs[postDescriptor.catalogDescriptor]?.getOriginalPost(postDescriptor)
  }

  override suspend fun getThreadPost(postDescriptor: PostDescriptor): IPostData? {
    return threads[postDescriptor.threadDescriptor]?.getPost(postDescriptor)
  }

  override suspend fun getOriginalPost(threadDescriptor: ThreadDescriptor): OriginalPostData? {
    return threads[threadDescriptor]?.getOriginalPost()
  }

  override suspend fun getLastPost(threadDescriptor: ThreadDescriptor): IPostData? {
    return threads[threadDescriptor]?.getLastPost()
  }

  override suspend fun getLastLoadedPostForIncrementalUpdate(threadDescriptor: ThreadDescriptor): IPostData? {
    return threads[threadDescriptor]?.getLastLoadedPostForIncrementalUpdate()
  }

  override suspend fun getNewPostsCount(postDescriptor: PostDescriptor): Int {
    return threads[postDescriptor.threadDescriptor]?.getNewPostsCount(postDescriptor) ?: 0
  }

  private suspend fun evictOld(
    cache: ConcurrentHashMap<ChanDescriptor, IChanCache>,
    maxCachedCount: Int
  ) {
    if (cache.size < maxCachedCount) {
      return
    }

    val sorted = cache.values
      .sortedBy { chanCache -> chanCache.lastUpdateTime }
      .toMutableList()

    var toRemove = cache.size / 3
    logcat { "evictOld() toRemove=$toRemove, total=${sorted.size}" }

    sorted.mutableIteration { mutableIterator, chanCache ->
      if (toRemove <= 0) {
        return@mutableIteration false
      }

      mutableIterator.remove()
      --toRemove

      logcat { "evictOld() removing ${chanCache.chanDescriptor}, toRemove=$toRemove" }

      return@mutableIteration true
    }
  }

}