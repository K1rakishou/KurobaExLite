package com.github.k1rakishou.kurobaexlite.interactors.navigation

import com.github.k1rakishou.kurobaexlite.managers.NavigationHistoryManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.local.NavigationElement
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

class ModifyNavigationHistory(
  private val navigationHistoryManager: NavigationHistoryManager,
  private val chanCache: ChanCache,
  private val parsedPostDataCache: ParsedPostDataCache
) {

  suspend fun addCatalog(catalogDescriptor: CatalogDescriptor) {
    navigationHistoryManager.addOrReorder(NavigationElement.Catalog(catalogDescriptor))
  }

  suspend fun addThread(threadDescriptor: ThreadDescriptor) {
    val title = parsedPostDataCache.formatThreadToolbarTitle(threadDescriptor.toOriginalPostDescriptor())
    val firstImageThumbnailUrl = chanCache.getOriginalPost(threadDescriptor)?.images?.firstOrNull()?.thumbnailAsString

    val navigationElement = NavigationElement.Thread(
      chanDescriptor = threadDescriptor,
      title = title,
      iconUrl = firstImageThumbnailUrl
    )

    navigationHistoryManager.addOrReorder(navigationElement)
  }

  suspend fun remove(chanDescriptor: ChanDescriptor) {
    navigationHistoryManager.remove(chanDescriptor)
  }

  suspend fun moveToTop(chanDescriptor: ChanDescriptor) {
    navigationHistoryManager.moveToTop(chanDescriptor)
  }

  suspend fun undoDeletion(prevIndex: Int, navigationElement: NavigationElement) {
    navigationHistoryManager.insert(prevIndex, navigationElement)
  }

}