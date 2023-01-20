package com.github.k1rakishou.kurobaexlite.model.source.local

import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

interface IPostHideLocalSource {
  suspend fun postHidesForThread(threadDescriptor: ThreadDescriptor): Map<PostDescriptor, ChanPostHide>

  suspend fun postHidesForCatalog(
    catalogDescriptor: CatalogDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): Map<PostDescriptor, ChanPostHide>

  suspend fun createOrUpdate(chanDescriptor: ChanDescriptor, chanPostHides: Collection<ChanPostHide>): Result<Unit>
  suspend fun update(chanPostHides: Collection<ChanPostHide>): Result<Unit>
  suspend fun delete(postDescriptors: Collection<PostDescriptor>): Result<Unit>
  suspend fun deleteOlderThanThreeMonths(): Result<Int>
}