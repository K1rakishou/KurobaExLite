package com.github.k1rakishou.kurobaexlite.model.source.local

import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import org.joda.time.DateTime

class PostHideLocalSource(
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) : IPostHideLocalSource {

  override suspend fun postHidesForThread(threadDescriptor: ThreadDescriptor): Map<PostDescriptor, ChanPostHide> {
    return kurobaExLiteDatabase.transaction {
      val chanPostHideEntities = chanPostHideDao.selectAllForThread(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )

      val resultMap = mutableMapWithCap<PostDescriptor, ChanPostHide>(chanPostHideEntities.size)

      chanPostHideEntities.forEach { chanPostHideEntity ->
        val postDescriptor = chanPostHideEntity.postKey.postDescriptor

        val chanPostHideReplyEntities = chanPostHideReplyDao.selectForPost(
          siteKey = postDescriptor.siteKeyActual,
          boardCode = postDescriptor.boardCode,
          threadNo = postDescriptor.threadNo,
          postNo = postDescriptor.postNo,
          postSubNo = postDescriptor.postSubNo
        )

        resultMap[postDescriptor] = ChanPostHide.fromChanPostHideEntity(
          chanPostHideEntity = chanPostHideEntity,
          chanPostHideReplyEntities = chanPostHideReplyEntities
        )
      }

      return@transaction resultMap
    }
      .onFailure { error -> logcatError(TAG) { "postHidesForThread() error: ${error.asLogIfImportantOrErrorMessage()}" } }
      .getOrDefault(emptyMap())
  }

  override suspend fun postHidesForCatalog(
    catalogDescriptor: CatalogDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): Map<PostDescriptor, ChanPostHide> {
    return kurobaExLiteDatabase.transaction {
      val chanPostHideEntities = postDescriptors
        .chunked(KurobaExLiteDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
        .flatMap { postDescriptorsBatch ->
          return@flatMap chanPostHideDao.selectAllForCatalog(
            siteKey = catalogDescriptor.siteKeyActual,
            boardCode = catalogDescriptor.boardCode,
            threadNos = postDescriptorsBatch.map { it.threadNo }
          )
        }

      val resultMap = mutableMapWithCap<PostDescriptor, ChanPostHide>(chanPostHideEntities.size)

      chanPostHideEntities.forEach { chanPostHideEntity ->
        val postDescriptor = chanPostHideEntity.postKey.postDescriptor

        val chanPostHideReplyEntities = chanPostHideReplyDao.selectForPost(
          siteKey = postDescriptor.siteKeyActual,
          boardCode = postDescriptor.boardCode,
          threadNo = postDescriptor.threadNo,
          postNo = postDescriptor.postNo,
          postSubNo = postDescriptor.postSubNo
        )

        resultMap[postDescriptor] = ChanPostHide.fromChanPostHideEntity(
          chanPostHideEntity = chanPostHideEntity,
          chanPostHideReplyEntities = chanPostHideReplyEntities
        )
      }

      return@transaction resultMap
    }
      .onFailure { error -> logcatError(TAG) { "postHidesForCatalog() error: ${error.asLogIfImportantOrErrorMessage()}" } }
      .getOrDefault(emptyMap())
  }

  override suspend fun createOrUpdate(chanDescriptor: ChanDescriptor, chanPostHides: Collection<ChanPostHide>): Result<Unit> {
    return kurobaExLiteDatabase.transaction {
      val chanPostHideEntities = chanPostHides.map { chanPostHide -> chanPostHide.toChanPostHideEntity() }
      chanPostHideDao.insertOrUpdateMany(chanPostHideEntities)

      val chanPostHideReplyEntities = chanPostHides
        .flatMap { chanPostHide -> chanPostHide.toChanPostHideReplyEntity() }
      chanPostHideReplyDao.insertOrUpdateMany(chanPostHideReplyEntities)
    }
  }

  override suspend fun update(chanPostHides: Collection<ChanPostHide>): Result<Unit> {
    return kurobaExLiteDatabase.transaction {
      val chanPostHideEntities = chanPostHides.map { chanPostHide -> chanPostHide.toChanPostHideEntity() }
      chanPostHideDao.updateMany(chanPostHideEntities)

      val chanPostHideReplyEntities = chanPostHides
        .flatMap { chanPostHide -> chanPostHide.toChanPostHideReplyEntity() }
      chanPostHideReplyDao.insertOrUpdateMany(chanPostHideReplyEntities)
    }
  }

  override suspend fun delete(postDescriptors: Collection<PostDescriptor>): Result<Unit> {
    return kurobaExLiteDatabase.transaction {
      postDescriptors.forEach { postDescriptor ->
        chanPostHideDao.delete(
          siteKey = postDescriptor.siteKeyActual,
          boardCode = postDescriptor.boardCode,
          threadNo = postDescriptor.threadNo,
          postNo = postDescriptor.postNo,
          postSubNo = postDescriptor.postSubNo
        )
      }
    }
  }

  override suspend fun deleteOlderThanThreeMonths(): Result<Int> {
    return kurobaExLiteDatabase.transaction {
      val threeMonthsAgo = DateTime.now().minusMonths(3).millis
      return@transaction chanPostHideDao.deleteOlderThan(threeMonthsAgo)
    }
  }

  companion object {
    private const val TAG = "PostHideLocalSource"
  }

}