package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.helpers.post_bind.PostBindProcessorCoordinator
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChanThreadManager(
  private val siteManager: SiteManager,
  private val chanPostCache: IChanPostCache,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val postBindProcessorCoordinator: PostBindProcessorCoordinator
) {
  private val _currentlyOpenedCatalogFlow = MutableStateFlow<CatalogDescriptor?>(null)
  val currentlyOpenedCatalogFlow: StateFlow<CatalogDescriptor?>
    get() = _currentlyOpenedCatalogFlow.asStateFlow()
  val currentlyOpenedCatalog: CatalogDescriptor?
    get() = _currentlyOpenedCatalogFlow.value

  private val _currentlyOpenedThreadFlow = MutableStateFlow<ThreadDescriptor?>(null)
  val currentlyOpenedThreadFlow: StateFlow<ThreadDescriptor?>
    get() = _currentlyOpenedThreadFlow.asStateFlow()
  val currentlyOpenedThread: ThreadDescriptor?
    get() = _currentlyOpenedThreadFlow.value

  suspend fun delete(chanDescriptor: ChanDescriptor) {
    parsedPostDataCache.delete(chanDescriptor)
    chanPostCache.delete(chanDescriptor)
    postBindProcessorCoordinator.removeCached(chanDescriptor)
  }

  suspend fun loadCatalog(catalogDescriptor: CatalogDescriptor?): Result<PostsLoadResult?> {
    _currentlyOpenedCatalogFlow.value = catalogDescriptor

    if (catalogDescriptor == null) {
      return Result.success(null)
    }

    val catalogDataSource = siteManager.bySiteKey(catalogDescriptor.siteKey)
      ?.catalogInfo()
      ?.catalogDataSource()
      ?: return Result.failure(CatalogNotSupported(catalogDescriptor.siteKey))

    return catalogDataSource.loadCatalog(catalogDescriptor)
      .map { catalogData -> chanPostCache.insertCatalogThreads(catalogDescriptor, catalogData.catalogThreads) }
  }

  suspend fun loadThread(threadDescriptor: ThreadDescriptor?): Result<PostsLoadResult?> {
    val loadingNewThread = _currentlyOpenedThreadFlow.value != threadDescriptor
    _currentlyOpenedThreadFlow.value = threadDescriptor

    if (threadDescriptor == null) {
      return Result.success(null)
    }

    val threadDataSource = siteManager.bySiteKey(threadDescriptor.siteKey)
      ?.threadInfo()
      ?.threadDataSource()
      ?: return Result.failure(ThreadNotSupported(threadDescriptor.siteKey))

    val lastCachedThreadPost = if (loadingNewThread) {
      // Do not use incremental thread update when loading a different thread from one we are currently in or if we
      // haven't loaded any threads yet.
      null
    } else {
      chanPostCache.getLastLoadedPostForIncrementalUpdate(threadDescriptor)
        ?.postDescriptor
    }

    return threadDataSource.loadThread(threadDescriptor, lastCachedThreadPost)
      .map { threadData ->
        return@map chanPostCache.insertThreadPosts(
          threadDescriptor = threadDescriptor,
          threadPostCells = threadData.threadPosts,
          isIncrementalUpdate = lastCachedThreadPost != null
        )
      }
  }

  suspend fun resetThreadLastFullUpdateTime(threadDescriptor: ThreadDescriptor) {
    chanPostCache.resetThreadLastFullUpdateTime(threadDescriptor)
  }

  class CatalogNotSupported(siteKey: SiteKey) : ClientException("Site \'${siteKey.key}\' does not support catalogs")
  class ThreadNotSupported(siteKey: SiteKey) : ClientException("Site \'${siteKey.key}\' does not support threads")

}