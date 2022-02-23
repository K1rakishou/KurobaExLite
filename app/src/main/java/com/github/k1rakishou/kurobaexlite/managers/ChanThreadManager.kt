package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChanThreadManager(
  private val siteManager: SiteManager
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

  suspend fun loadCatalog(catalogDescriptor: CatalogDescriptor?): Result<CatalogData?> {
    if (catalogDescriptor == null) {
      _currentlyOpenedCatalogFlow.value = null
      return Result.success(null)
    }

    val catalogDataSource = siteManager.bySiteKey(catalogDescriptor.siteKey)
      ?.catalogInfo()
      ?.catalogDataSource()
      ?: return Result.failure(CatalogNotSupported(catalogDescriptor.siteKey))

    val result = catalogDataSource.loadCatalog(catalogDescriptor)
    if (result.isSuccess) {
      _currentlyOpenedCatalogFlow.value = catalogDescriptor
    }

    return result
  }

  suspend fun loadThread(threadDescriptor: ThreadDescriptor?): Result<ThreadData?> {
    if (threadDescriptor == null) {
      _currentlyOpenedThreadFlow.value = null
      return Result.success(null)
    }

    val threadDataSource = siteManager.bySiteKey(threadDescriptor.siteKey)
      ?.threadInfo()
      ?.threadDataSource()
      ?: return Result.failure(ThreadNotSupported(threadDescriptor.siteKey))

    val result = threadDataSource.loadThread(threadDescriptor)
    if (result.isSuccess) {
      _currentlyOpenedThreadFlow.value = threadDescriptor
    }

    return result
  }

  class CatalogNotSupported(siteKey: SiteKey) : ClientException("Site \'${siteKey.key}\' does not support catalogs")
  class ThreadNotSupported(siteKey: SiteKey) : ClientException("Site \'${siteKey.key}\' does not support threads")

}