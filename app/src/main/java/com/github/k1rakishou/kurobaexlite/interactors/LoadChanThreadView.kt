package com.github.k1rakishou.kurobaexlite.interactors

import com.github.k1rakishou.kurobaexlite.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadViewManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog

class LoadChanThreadView(
  private val chanThreadViewManager: ChanThreadViewManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  suspend fun execute(threadDescriptor: ThreadDescriptor): ChanThreadView? {
    val chanThreadViewFromCache = chanThreadViewManager.read(threadDescriptor)
    if (chanThreadViewFromCache != null) {
      return chanThreadViewFromCache
    }

    val chanThreadViewFromDatabaseResult = kurobaExLiteDatabase.call {
      val chanThreadViewFromDatabase = chanThreadViewDao.select(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )

      val chanThreadView = if (chanThreadViewFromDatabase == null) {
        ChanThreadView(threadDescriptor)
      } else {
        ChanThreadView(
          threadDescriptor = threadDescriptor,
          lastViewedPostDescriptorForIndicator = null,
          lastViewedPostDescriptor = chanThreadViewFromDatabase.lastViewedPost?.postDescriptor(threadDescriptor),
          lastLoadedPostDescriptor = chanThreadViewFromDatabase.lastLoadedPost?.postDescriptor(threadDescriptor)
        )
      }

      return@call chanThreadView
    }

    val chanThreadViewFromDatabase = if (chanThreadViewFromDatabaseResult.isFailure) {
      val error = chanThreadViewFromDatabaseResult.exceptionOrThrow()
      logcatError { "chanThreadViewDao.select() error: ${error.asLog()}" }

      return null
    } else {
      chanThreadViewFromDatabaseResult.getOrThrow()
    }

    chanThreadViewManager.insertOrUpdate(
      threadDescriptor = threadDescriptor,
      updater = {
        lastViewedPostDescriptor = chanThreadViewFromDatabase.lastViewedPostDescriptor
        lastLoadedPostDescriptor = chanThreadViewFromDatabase.lastLoadedPostDescriptor
      }
    )

    return chanThreadViewFromDatabase
  }

}