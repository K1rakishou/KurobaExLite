package com.github.k1rakishou.kurobaexlite.model.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PostHideRepository : IPostHideRepository {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val hiddenPosts = mutableMapOf<ChanDescriptor, MutableSet<PostDescriptor>>()
  @GuardedBy("mutex")
  private val postHides = mutableMapWithCap<PostDescriptor, ChanPostHide>(128)

  private val _postsToReparseFlow = MutableSharedFlow<Collection<PostDescriptor>>(extraBufferCapacity = Channel.UNLIMITED)
  override val postsToReparseFlow: SharedFlow<Collection<PostDescriptor>>
    get() = _postsToReparseFlow.asSharedFlow()

  override suspend fun postHidesForChanDescriptor(chanDescriptor: ChanDescriptor): Map<PostDescriptor, ChanPostHide> {
    return mutex.withLock {
      return@withLock hiddenPosts[chanDescriptor]
        ?.mapNotNull { postDescriptor -> postHides[postDescriptor] }
        ?.associateBy { chanPostHide -> chanPostHide.postDescriptor }
        ?: emptyMap()
    }
  }

  override suspend fun postHideForPostDescriptor(postDescriptor: PostDescriptor): ChanPostHide? {
    return mutex.withLock { postHides[postDescriptor] }
  }

  override suspend fun createOrUpdate(chanDescriptor: ChanDescriptor, chanPostHides: Collection<ChanPostHide>) {
    val toEmit = mutex.withLock {
      val toEmit = mutableListOf<PostDescriptor>()

      for (chanPostHide in chanPostHides) {
        val hideFlagsDiffer = postHides[chanPostHide.postDescriptor]
          ?.hideFlagsDiffer(chanPostHide)
          ?: true

        if (!hideFlagsDiffer) {
          continue
        }

        val innerSet = hiddenPosts.getOrPut(
          key = chanDescriptor,
          defaultValue = { mutableSetWithCap(32) }
        )

        if (postHides[chanPostHide.postDescriptor] == chanPostHide) {
          continue
        }

        innerSet.add(chanPostHide.postDescriptor)
        postHides[chanPostHide.postDescriptor] = chanPostHide
        toEmit += chanPostHide.postDescriptor
      }

      return@withLock toEmit
    }

    if (toEmit.isNotEmpty()) {
      _postsToReparseFlow.emit(toEmit)
    }
  }

  override suspend fun update(postDescriptor: PostDescriptor, updater: (ChanPostHide) -> ChanPostHide) {
    update(listOf(postDescriptor), updater)
  }

  override suspend fun update(
    postDescriptors: Collection<PostDescriptor>,
    updater: (ChanPostHide) -> ChanPostHide
  ) {
    val toEmit = mutex.withLock {
      val toEmit = mutableListOf<PostDescriptor>()

      for (postDescriptor in postDescriptors) {
        val prevChanPostHide = postHides[postDescriptor]?.copy()
          ?: continue
        val newChanPostHide = updater(prevChanPostHide)

        if (prevChanPostHide == newChanPostHide) {
          continue
        }

        postHides[postDescriptor] = newChanPostHide
        toEmit += newChanPostHide.postDescriptor
      }

      return@withLock toEmit
    }

    if (toEmit.isNotEmpty()) {
      _postsToReparseFlow.emit(toEmit)
    }
  }

  override suspend fun isPostHidden(postDescriptor: PostDescriptor): Boolean {
    return mutex.withLock {
      val chanPostHide = postHides[postDescriptor]
      if (chanPostHide == null) {
        return@withLock false
      }

      if (chanPostHide.manuallyUnhidden) {
        return@withLock false
      }

      return@withLock true
    }
  }

}