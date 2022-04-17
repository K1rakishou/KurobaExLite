package com.github.k1rakishou.kurobaexlite.interactors.marked_post

import com.github.k1rakishou.kurobaexlite.managers.MarkedPostManager
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPost
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPostType
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog
import logcat.logcat

class LoadMarkedPosts(
  private val markedPostManager: MarkedPostManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun load(threadDescriptor: ThreadDescriptor) {
    if (markedPostManager.isAlreadyLoadedForThread(threadDescriptor)) {
      return
    }

    val markedPostsInThreadResult = kurobaExLiteDatabase.call {
      val markedPostEntitiesInThread = markedPostDao.select(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )

      return@call markedPostEntitiesInThread.mapNotNull { markedPostEntity ->
        val markedPostType = MarkedPostType.fromTypRaw(markedPostEntity.type)
          ?: return@mapNotNull null

        MarkedPost(
          postDescriptor = markedPostEntity.postKey.postDescriptor,
          markedPostType = markedPostType
        )
      }
    }.onFailure { error -> logcat(TAG) { "markedPostDao.select() error: ${error.asLog()}" } }

    val markedPostsInThread = if (markedPostsInThreadResult.isFailure) {
      return
    } else {
      markedPostsInThreadResult.getOrThrow()
    }

    markedPostManager.insert(threadDescriptor, markedPostsInThread)
  }

  companion object {
    private const val TAG = "LoadMarkedPosts"
  }

}