package com.github.k1rakishou.kurobaexlite.model.cache

import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.mutableIteration
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.concurrent.ConcurrentHashMap
import logcat.logcat

class ChanCache {
  private val maxCachedCatalogs = 3 // TODO(KurobaEx): change this number for production
  private val maxCachedThreads = 5 // TODO(KurobaEx): change this number for production

  private val catalogs = ConcurrentHashMap<CatalogDescriptor, ChanCatalogCache>()
  private val threads = ConcurrentHashMap<ThreadDescriptor, ChanThreadCache>()

  suspend fun onCatalogOrThreadAccessed(chanDescriptor: ChanDescriptor) {
    when (chanDescriptor) {
      is CatalogDescriptor -> catalogs[chanDescriptor]?.onCatalogAccessed()
      is ThreadDescriptor -> threads[chanDescriptor]?.onThreadAccessed()
    }
  }

  suspend fun insertCatalogThreads(
    catalogDescriptor: CatalogDescriptor,
    catalogThreads: Collection<IPostData>
  ): PostsLoadResult {
    if (catalogThreads.isEmpty()) {
      return PostsLoadResult.EMPTY
    }

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

    return PostsLoadResult(
      newPosts = catalogThreads.toList(),
      updatedPosts = emptyList()
    )
  }

  suspend fun insertThreadPosts(
    threadDescriptor: ThreadDescriptor,
    threadPostCells: Collection<IPostData>
  ): PostsLoadResult {
    if (threadPostCells.isEmpty()) {
      return PostsLoadResult.EMPTY
    }

    val chanThread = threads.getOrPut(
      key = threadDescriptor,
      defaultValue = { ChanThreadCache(threadDescriptor) }
    )

    val hasPosts = chanThread.hasPosts()
    val postsMergeResult = chanThread.insert(threadPostCells)

    // Only run evictOld() routine when inserting new threads into the cache
    if (!hasPosts) {
      evictOld(threads as ConcurrentHashMap<ChanDescriptor, IChanCache>, maxCachedThreads)
    }

    return postsMergeResult
  }

  suspend fun getManyForDescriptor(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): List<IPostData> {
    val resultList = mutableListWithCap<IPostData>(postDescriptors.size)

    when (chanDescriptor) {
      is CatalogDescriptor -> {
        val chanCatalog = catalogs[chanDescriptor]
        if (chanCatalog != null) {
          resultList.addAll(chanCatalog.getPostDataList())
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

  suspend fun getThreadPosts(threadDescriptor: ThreadDescriptor): List<IPostData> {
    return threads[threadDescriptor]?.getAll() ?: emptyList()
  }

  suspend fun getCatalogThreads(catalogDescriptor: CatalogDescriptor): List<OriginalPostData> {
    return catalogs[catalogDescriptor]?.getPostDataList() ?: emptyList()
  }

  suspend fun getPost(postDescriptor: PostDescriptor): IPostData? {
    return threads[postDescriptor.threadDescriptor]?.getPost(postDescriptor)
  }

  suspend fun getLastPost(threadDescriptor: ThreadDescriptor): IPostData? {
    return threads[threadDescriptor]?.getLastPost()
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