package com.github.k1rakishou.kurobaexlite.model.repository

import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.flow.SharedFlow

interface IPostHideRepository {
  val postsToReparseFlow: SharedFlow<Collection<PostDescriptor>>

  suspend fun postHidesForChanDescriptor(chanDescriptor: ChanDescriptor): Map<PostDescriptor, ChanPostHide>
  suspend fun postHideForPostDescriptor(postDescriptor: PostDescriptor): ChanPostHide?
  suspend fun createOrUpdate(chanDescriptor: ChanDescriptor, chanPostHides: Collection<ChanPostHide>)
  suspend fun update(postDescriptor: PostDescriptor, updater: (ChanPostHide) -> ChanPostHide)
  suspend fun update(postDescriptors: Collection<PostDescriptor>, updater: (ChanPostHide) -> ChanPostHide)
  suspend fun isPostHidden(postDescriptor: PostDescriptor): Boolean
}