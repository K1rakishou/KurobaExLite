package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.util.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalogView
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex

class ChanViewManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val chanThreadViews = mutableMapOf<ThreadDescriptor, ChanThreadView>()
  @GuardedBy("mutex")
  private val chanCatalogViews = mutableMapOf<CatalogDescriptor, ChanCatalogView>()

  suspend fun insertOrUpdate(
    threadDescriptor: ThreadDescriptor,
    updater: ChanThreadView.() -> Unit
  ): ChanThreadView {
    return mutex.withLockNonCancellable {
      val chanThreadView = chanThreadViews.getOrPut(
        key = threadDescriptor,
        defaultValue = { ChanThreadView(threadDescriptor = threadDescriptor) }
      )

      updater(chanThreadView)
      chanThreadViews[threadDescriptor] = chanThreadView

      return@withLockNonCancellable chanThreadView
    }
  }

  suspend fun read(threadDescriptor: ThreadDescriptor): ChanThreadView? {
    return mutex.withLockNonCancellable { chanThreadViews[threadDescriptor] }
  }

  suspend fun insertOrUpdate(
    catalogDescriptor: CatalogDescriptor,
    updater: ChanCatalogView.() -> Unit
  ): ChanCatalogView {
    return mutex.withLockNonCancellable {
      val chanCatalogView = chanCatalogViews.getOrPut(
        key = catalogDescriptor,
        defaultValue = { ChanCatalogView(catalogDescriptor = catalogDescriptor) }
      )

      updater(chanCatalogView)
      chanCatalogViews[catalogDescriptor] = chanCatalogView

      return@withLockNonCancellable chanCatalogView
    }
  }

  suspend fun read(catalogDescriptor: CatalogDescriptor): ChanCatalogView? {
    return mutex.withLockNonCancellable { chanCatalogViews[catalogDescriptor] }
  }

}