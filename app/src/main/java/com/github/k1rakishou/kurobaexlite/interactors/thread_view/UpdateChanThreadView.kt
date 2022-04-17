package com.github.k1rakishou.kurobaexlite.interactors.thread_view

import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.data.entity.CatalogOrThreadKey
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanThreadViewEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadLocalPostKey
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.asLog
import logcat.logcat

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
        catalogOrThreadKey = CatalogOrThreadKey.fromThreadDescriptor(threadDescriptor),
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
    }.onFailure { error -> logcat(TAG) { "chanThreadViewDao.insert() error: ${error.asLog()}" } }

    if (callResult.isFailure) {
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

  companion object {
    private const val TAG = "UpdateChanThreadView"
  }

}