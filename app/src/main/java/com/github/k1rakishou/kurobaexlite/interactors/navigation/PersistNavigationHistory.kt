package com.github.k1rakishou.kurobaexlite.interactors.navigation

import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.managers.NavigationHistoryManager
import com.github.k1rakishou.kurobaexlite.model.data.entity.CatalogOrThreadKey
import com.github.k1rakishou.kurobaexlite.model.data.entity.NavigationHistoryEntity
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat

class PersistNavigationHistory(
  private val navigationHistoryManager: NavigationHistoryManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  private val scope = KurobaCoroutineScope(dispatcher = Dispatchers.IO)
  private var persistJob: Job? = null

  fun persist() {
    persistJob?.cancel()
    persistJob = scope.launch {
      // Wait 1 second before trying to persist the data
      delay(1000L)

      kurobaExLiteDatabase.transaction {
        val wholeNavigationHistory = navigationHistoryManager.all()
        if (wholeNavigationHistory.isEmpty()) {
          logcat(TAG) { "wholeNavigationHistory is empty, deleting everything from the database" }
          navigationHistoryDao.deleteAll()
          return@transaction
        }

        val navigationHistoryEntityList = wholeNavigationHistory.mapIndexed { sortOrder, navigationElement ->
          NavigationHistoryEntity(
            catalogOrThreadKey = CatalogOrThreadKey.fromChanDescriptor(navigationElement.chanDescriptor),
            title = navigationElement.elementTitle,
            iconUrl = navigationElement.elementIconUrl,
            sortOrder = sortOrder
          )
        }

        withContext(NonCancellable) {
          // This is how we limit the max amount of navigation history elements in the database
          navigationHistoryDao.deleteAll()
          navigationHistoryDao.insertOrUpdateMany(navigationHistoryEntityList)
        }

        logcat(TAG) { "persisted ${navigationHistoryEntityList.size} navigation elements" }
      }.onFailure { error -> logcat(TAG) { "persist() error: ${error.asLog()}" } }
    }
  }

  companion object {
    private const val TAG = "PersistNavigationHistory"
  }

}