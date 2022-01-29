package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.model.ChanDataSource
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChanThreadManager(
  private val chanDataSource: ChanDataSource
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
    _currentlyOpenedCatalogFlow.value = catalogDescriptor

    if (catalogDescriptor == null) {
      return Result.success(null)
    }

    return chanDataSource.loadCatalog(catalogDescriptor)
  }

  suspend fun loadThread(threadDescriptor: ThreadDescriptor?): Result<ThreadData?> {
    _currentlyOpenedThreadFlow.value = threadDescriptor

    if (threadDescriptor == null) {
      return Result.success(null)
    }

    return chanDataSource.loadThread(threadDescriptor)
  }

}