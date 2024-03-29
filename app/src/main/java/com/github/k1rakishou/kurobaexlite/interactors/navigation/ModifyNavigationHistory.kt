package com.github.k1rakishou.kurobaexlite.interactors.navigation

import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.NavigationHistoryManager
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.data.local.NavigationElement
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository

class ModifyNavigationHistory(
  private val navigationHistoryManager: NavigationHistoryManager,
  private val chanPostCache: IChanPostCache,
  private val parsedPostDataRepository: ParsedPostDataRepository,
  private val appSettings: AppSettings
) {

  suspend fun addCatalog(catalogDescriptor: CatalogDescriptor) {
    if (!appSettings.historyEnabled.read()) {
      return
    }

    navigationHistoryManager.addOrReorder(NavigationElement.Catalog(catalogDescriptor))
  }

  suspend fun addThread(threadDescriptor: ThreadDescriptor) {
    if (!appSettings.historyEnabled.read()) {
      return
    }

    val title = parsedPostDataRepository.formatThreadToolbarTitle(
      postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
      maxLength = AppConstants.navHistoryMaxTitleLength
    )

    val firstImageThumbnailUrl = chanPostCache.getOriginalPost(threadDescriptor)
      ?.images
      ?.firstOrNull()
      ?.thumbnailAsString

    val navigationElement = NavigationElement.Thread(
      chanDescriptor = threadDescriptor,
      title = title,
      iconUrl = firstImageThumbnailUrl
    )

    navigationHistoryManager.addOrReorder(navigationElement)
  }

  suspend fun addManyThreads(threadDescriptors: Collection<ThreadDescriptor>) {
    if (threadDescriptors.isEmpty()) {
      return
    }

    val navigationElements = threadDescriptors.map { threadDescriptor ->
      val title = parsedPostDataRepository.formatThreadToolbarTitle(
        postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
        maxLength = AppConstants.navHistoryMaxTitleLength
      )

      var originalPost = chanPostCache.getOriginalPost(threadDescriptor)
      if (originalPost == null) {
        originalPost = chanPostCache.getCatalogPost(threadDescriptor.toOriginalPostDescriptor())
      }

      val firstImageThumbnailUrl = originalPost
        ?.images
        ?.firstOrNull()
        ?.thumbnailAsString

      return@map NavigationElement.Thread(
        chanDescriptor = threadDescriptor,
        title = title,
        iconUrl = firstImageThumbnailUrl
      )
    }

    navigationHistoryManager.addMany(navigationElements)
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