package com.github.k1rakishou.kurobaexlite.interactors

import com.github.k1rakishou.kurobaexlite.database.ChanThreadViewEntity
import com.github.k1rakishou.kurobaexlite.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.database.ThreadKey
import com.github.k1rakishou.kurobaexlite.database.ThreadLocalPostKey
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadViewManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog

class UpdateChanThreadView(
  private val chanThreadViewManager: ChanThreadViewManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  suspend fun execute(
    threadDescriptor: ThreadDescriptor,
    threadLastPostDescriptor: PostDescriptor?,
    threadBoundPostDescriptor: PostDescriptor?,
    isBottomOnThreadReached: Boolean
  ): ChanThreadView? {
    val updatedChanThreadView = chanThreadViewManager.insertOrUpdate(threadDescriptor) {
      if (threadLastPostDescriptor != null) {
        lastLoadedPostDescriptor = maxOfPostDescriptors(
          one = lastLoadedPostDescriptor,
          other = threadLastPostDescriptor
        )
      }

      if (
        lastViewedPostDescriptorForIndicator == null &&
        (threadLastPostDescriptor != null || isBottomOnThreadReached)
      ) {
        lastViewedPostDescriptorForIndicator = maxOfPostDescriptors(
          one = lastViewedPostDescriptorForIndicator,
          other = threadLastPostDescriptor
        )
      }

      if (threadBoundPostDescriptor != null) {
        lastViewedPostDescriptor = threadBoundPostDescriptor
      }
    }

    val callResult = kurobaExLiteDatabase.call {
      val chanThreadViewEntity = ChanThreadViewEntity(
        threadKey = ThreadKey.fromThreadDescriptor(threadDescriptor),
        lastViewedPost = updatedChanThreadView.lastViewedPostDescriptor
          ?.let { ThreadLocalPostKey.fromPostDescriptor(it) },
        lastLoadedPost = updatedChanThreadView.lastLoadedPostDescriptor
          ?.let { ThreadLocalPostKey.fromPostDescriptor(it) }
      )

      chanThreadViewDao.insert(chanThreadViewEntity)
    }

    if (callResult.isFailure) {
      val error = callResult.exceptionOrThrow()
      logcatError { "chanThreadViewDao.insert() error: ${error.asLog()}" }

      return null
    }

    return updatedChanThreadView
  }

  private fun maxOfPostDescriptors(one: PostDescriptor?, other: PostDescriptor?): PostDescriptor? {
    if (one == null && other == null) {
      return null
    }

    if (one == null || other == null) {
      if (one != null) {
        return one
      }

      return other
    }

    if (one.threadNo > other.threadNo) {
      return one
    } else if (one.threadNo < other.threadNo) {
      return other
    }

    if (one.postNo > other.postNo) {
      return one
    } else if (one.postNo < other.postNo) {
      return other
    }

    if (one.postSubNo > other.postSubNo) {
      return one
    } else if (one.postSubNo < other.postSubNo) {
      return other
    }

    return other
  }

}