package com.github.k1rakishou.kurobaexlite.interactors

import com.github.k1rakishou.kurobaexlite.database.ChanThreadViewEntity
import com.github.k1rakishou.kurobaexlite.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.database.ThreadKey
import com.github.k1rakishou.kurobaexlite.database.ThreadLocalPostKey
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog

class UpdateChanThreadView(
  private val chanViewManager: ChanViewManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  suspend fun execute(
    threadDescriptor: ThreadDescriptor,
    threadLastPostDescriptor: PostDescriptor?,
    firstVisiblePost: PostDescriptor?,
    lastVisiblePost: PostDescriptor?,
    postListTouchingBottom: Boolean?
  ): ChanThreadView? {
    val updatedChanThreadView = chanViewManager.insertOrUpdate(threadDescriptor) {
      if (threadLastPostDescriptor != null) {
        lastLoadedPostDescriptor = maxOfPostDescriptors(
          one = lastLoadedPostDescriptor,
          other = threadLastPostDescriptor
        )
      }

      if (postListTouchingBottom == true) {
        lastViewedPDForIndicator = null
      } else if (lastViewedPDForIndicator == null) {
        lastViewedPDForIndicator = maxOfPostDescriptors(
          one = threadLastPostDescriptor,
          other = lastLoadedPostDescriptor
        )
      }

      if (firstVisiblePost != null) {
        lastViewedPDForScroll = firstVisiblePost
      }

      if (lastVisiblePost != null) {
        lastViewedPDForNewPosts = lastVisiblePost
      }
    }

    val callResult = kurobaExLiteDatabase.call {
      val chanThreadViewEntity = ChanThreadViewEntity(
        threadKey = ThreadKey.fromThreadDescriptor(threadDescriptor),
        lastViewedPostForIndicator = updatedChanThreadView.lastViewedPDForIndicator
          ?.let { ThreadLocalPostKey.fromPostDescriptor(it) },
        lastViewedPostForScroll = updatedChanThreadView.lastViewedPDForScroll
          ?.let { ThreadLocalPostKey.fromPostDescriptor(it) },
        lastViewedPostForNewPosts = updatedChanThreadView.lastViewedPDForNewPosts
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