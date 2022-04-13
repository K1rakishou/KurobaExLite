package com.github.k1rakishou.kurobaexlite.interactors

import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog
import logcat.logcat

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
    }.onFailure { error -> logcat(TAG) { "chanThreadViewDao.select() error: ${error.asLog()}" } }

    val chanThreadViewFromDatabase = if (chanThreadViewFromDatabaseResult.isFailure) {
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

  companion object {
    private const val TAG = "LoadChanThreadView"
  }

}