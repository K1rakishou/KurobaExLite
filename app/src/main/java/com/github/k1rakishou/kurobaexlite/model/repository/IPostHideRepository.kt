package com.github.k1rakishou.kurobaexlite.model.repository

import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.flow.SharedFlow

interface IPostHideRepository {
  val postsToReparseFlow: SharedFlow<Collection<PostDescriptor>>

  suspend fun postHidesForThread(threadDescriptor: ThreadDescriptor): Map<PostDescriptor, ChanPostHide>
  suspend fun postHidesForCatalog(
    catalogDescriptor: CatalogDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): Map<PostDescriptor, ChanPostHide>

  suspend fun postHideForPostDescriptor(postDescriptor: PostDescriptor): ChanPostHide?
  suspend fun isPostHidden(postDescriptor: PostDescriptor): Boolean
  suspend fun createOrUpdate(chanDescriptor: ChanDescriptor, chanPostHides: Collection<ChanPostHide>): Result<Unit>
  suspend fun update(postDescriptor: PostDescriptor, updater: (ChanPostHide) -> ChanPostHide): Result<Unit>
  suspend fun update(postDescriptors: Collection<PostDescriptor>, updater: (ChanPostHide) -> ChanPostHide): Result<Unit>
  suspend fun delete(postDescriptor: PostDescriptor): Result<Unit>
  suspend fun delete(postDescriptors: Collection<PostDescriptor>): Result<Unit>
  suspend fun deleteOlderThanThreeMonths(): Result<Int>
}