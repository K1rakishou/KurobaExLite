package com.github.k1rakishou.kurobaexlite.interactors.marked_post

import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.MarkedPostManager
import com.github.k1rakishou.kurobaexlite.model.data.entity.MarkedPostEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.PostKey
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPostType
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import logcat.asLog
import logcat.logcat

class ModifyMarkedPosts(
  private val markedPostManager: MarkedPostManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun markPostAsMine(postDescriptor: PostDescriptor): Boolean {
    val marked = markedPostManager.markPost(postDescriptor, MarkedPostType.MyPost)
    if (!marked) {
      // Post is already marked as mine
      logcat(TAG) { "markPostAsMine($postDescriptor) already marked" }
      return false
    }

    val insertMarkedPostResult = kurobaExLiteDatabase.call {
      val markedPostEntity = MarkedPostEntity(
        postKey = PostKey.fromPostDescriptor(postDescriptor),
        type = MarkedPostType.MyPost.type
      )

      markedPostDao.insert(markedPostEntity)
    }.onFailure { error -> logcatError(TAG) { "markedPostDao.insert() error: ${error.asLog()}" } }

    if (insertMarkedPostResult.isFailure) {
      markedPostManager.unmarkPost(postDescriptor, MarkedPostType.MyPost)
      return false
    }

    logcat(TAG) { "markPostAsMine($postDescriptor) success" }
    return true
  }

  suspend fun unmarkPostAsMine(postDescriptor: PostDescriptor): Boolean {
    val unmarked = markedPostManager.unmarkPost(postDescriptor, MarkedPostType.MyPost)
    if (!unmarked) {
      // Post is already not marked as mine
      logcat(TAG) { "unmarkPostAsMine($postDescriptor) already unmarked" }
      return false
    }

    val deleteMarkedPostResult = kurobaExLiteDatabase.call {
      markedPostDao.delete(
        siteKey = postDescriptor.siteKeyActual,
        boardCode = postDescriptor.boardCode,
        threadNo = postDescriptor.threadNo,
        postNo = postDescriptor.postNo,
        postSubNo = postDescriptor.postSubNo
      )
    }.onFailure { error -> logcatError(TAG) { "markedPostDao.delete() error: ${error.asLog()}" } }

    if (deleteMarkedPostResult.isFailure) {
      markedPostManager.markPost(postDescriptor, MarkedPostType.MyPost)
      return false
    }

    logcat(TAG) { "unmarkPostAsMine($postDescriptor) success" }
    return true
  }

  companion object {
    private const val TAG = "ModifyMarkedPosts"
  }

}