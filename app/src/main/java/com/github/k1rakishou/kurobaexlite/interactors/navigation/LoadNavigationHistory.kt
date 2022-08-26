package com.github.k1rakishou.kurobaexlite.interactors.navigation

import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
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
        logcat(TAG) { "navigationHistoryDao.selectAllOrdered(maxCount) returned empty list" }
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
      logcat(TAG) { "loaded ${navigationElementList.size} navigation elements" }
    }.onFailure { error -> logcat(TAG) { "loadFromDatabase() error: ${error.asLog()}" } }
  }

  suspend fun loadLastVisitedThread(): LastVisitedThread? {
    return kurobaExLiteDatabase.call {
      val navigationHistoryEntity = navigationHistoryDao.selectLastVisitedThreadEntity()
        ?: return@call null

      return@call LastVisitedThread(
        threadDescriptor = navigationHistoryEntity.catalogOrThreadKey.threadDescriptor,
        title = navigationHistoryEntity.title
      )
    }
      .onFailure { error -> logcatError(TAG) { "selectLastVisitedThreadEntity() error: ${error.asLog()}" } }
      .getOrNull()
  }

  suspend fun lastVisitedCatalog(): LastVisitedCatalog? {
    return kurobaExLiteDatabase.call {
      val navigationHistoryEntity = navigationHistoryDao.selectLastVisitedCatalogEntity()
        ?: return@call null

      return@call LastVisitedCatalog(
        catalogDescriptor = navigationHistoryEntity.catalogOrThreadKey.catalogDescriptor
      )
    }
      .onFailure { error -> logcatError(TAG) { "selectLastVisitedCatalogEntity() error: ${error.asLog()}" } }
      .getOrNull()
  }

  data class LastVisitedCatalog(
    val catalogDescriptor: CatalogDescriptor
  )

  data class LastVisitedThread(
    val threadDescriptor: ThreadDescriptor,
    val title: String?,
  )

  companion object {
    private const val TAG = "LoadNavigationHistory"
  }

}