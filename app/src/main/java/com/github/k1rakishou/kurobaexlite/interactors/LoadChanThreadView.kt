package com.github.k1rakishou.kurobaexlite.interactors

import com.github.k1rakishou.kurobaexlite.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog

class LoadChanThreadView(
  private val chanViewManager: ChanViewManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  suspend fun execute(threadDescriptor: ThreadDescriptor): ChanThreadView? {
    val chanThreadViewFromCache = chanViewManager.read(threadDescriptor)
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
          lastViewedPDForIndicator = chanThreadViewFromDatabase.lastViewedPostForIndicator?.postDescriptor(threadDescriptor),
          lastViewedPDForScroll = chanThreadViewFromDatabase.lastViewedPostForScroll?.postDescriptor(threadDescriptor),
          lastViewedPDForNewPosts = chanThreadViewFromDatabase.lastViewedPostForNewPosts?.postDescriptor(threadDescriptor),
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

    chanViewManager.insertOrUpdate(
      threadDescriptor = threadDescriptor,
      updater = {
        lastViewedPDForIndicator = chanThreadViewFromDatabase.lastViewedPDForIndicator
        lastViewedPDForScroll = chanThreadViewFromDatabase.lastViewedPDForScroll
        lastViewedPDForNewPosts = chanThreadViewFromDatabase.lastViewedPDForNewPosts
        lastLoadedPostDescriptor = chanThreadViewFromDatabase.lastLoadedPostDescriptor
      }
    )

    return chanThreadViewFromDatabase
  }

}