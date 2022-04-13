package com.github.k1rakishou.kurobaexlite.interactors.navigation

import com.github.k1rakishou.kurobaexlite.managers.NavigationHistoryManager
import com.github.k1rakishou.kurobaexlite.model.data.local.NavigationElement
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog
import logcat.logcat

class LoadNavigationHistory(
  private val navigationHistoryManager: NavigationHistoryManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun loadFromDatabase(maxCount: Int) {
    logcat(TAG) { "loadFromDatabase($maxCount)" }

    if (maxCount <= 0) {
      return
    }

    kurobaExLiteDatabase.transaction {
      val navigationHistoryEntityList = navigationHistoryDao.selectAllOrdered(maxCount)
      if (navigationHistoryEntityList.isEmpty()) {
        return@transaction
      }

      val navigationElementList = navigationHistoryEntityList.map { navigationHistoryEntity ->
        return@map when (val chanDescriptor = navigationHistoryEntity.catalogOrThreadKey.chanDescriptor) {
          is CatalogDescriptor -> {
            NavigationElement.Catalog(chanDescriptor)
          }
          is ThreadDescriptor -> {
            NavigationElement.Thread(
              chanDescriptor = chanDescriptor,
              title = navigationHistoryEntity.title,
              iconUrl = navigationHistoryEntity.iconUrl
            )
          }
        }
      }

      navigationHistoryManager.load(navigationElementList)

      // This is how we limit the max amount of navigation history elements in the database
      navigationHistoryDao.deleteAll()

      logcat(TAG) { "loaded ${navigationElementList.size} navigation elements" }
    }.onFailure { error -> logcat(TAG) { "loadFromDatabase() error: ${error.asLog()}" } }
  }

  companion object {
    private const val TAG = "LoadNavigationHistory"
  }

}