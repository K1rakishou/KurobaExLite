package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.interactors.navigation.ModifyNavigationHistory
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.atomic.AtomicBoolean

class AddToHistoryAllCatalogThreads(
  private val appScope: CoroutineScope,
  private val modifyNavigationHistory: ModifyNavigationHistory,
  private val chanPostCache: IChanPostCache,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val working = AtomicBoolean(false)

  fun await(catalogDescriptor: CatalogDescriptor) {
    appScope.launch(dispatcher) {
      if (!working.compareAndSet(false, true)) {
        return@launch
      }

      try {
        doTheWork(catalogDescriptor)
      } finally {
        working.set(false)
      }
    }
  }

  private suspend fun doTheWork(catalogDescriptor: CatalogDescriptor) {
    val catalogThreads = chanPostCache.getCatalogThreads(catalogDescriptor)
    if (catalogThreads.isEmpty()) {
      logcat(TAG) { "catalogThreads are empty" }
      return
    }

    val threadDescriptors = catalogThreads.map { it.postDescriptor.threadDescriptor }
    modifyNavigationHistory.addManyThreads(threadDescriptors)

    logcat(TAG) { "modifyNavigationHistory.addManyThreads(${threadDescriptors.size}) end" }
  }

  companion object {
    private const val TAG = "AddToHistoryAllCatalogThreads"
  }

}