package com.github.k1rakishou.kurobaexlite.model.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.util.linkedMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.local.IPostHideLocalSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class PostHideRepository(
  private val postHideLocalSource: IPostHideLocalSource
) : IPostHideRepository {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val hiddenPosts = mutableMapOf<ChanDescriptor, MutableSet<PostDescriptor>>()
  @GuardedBy("mutex")
  private val postHides = mutableMapWithCap<PostDescriptor, ChanPostHide>(128)

  private val _postsToReparseFlow = MutableSharedFlow<Collection<PostDescriptor>>(extraBufferCapacity = Channel.UNLIMITED)
  override val postsToReparseFlow: SharedFlow<Collection<PostDescriptor>>
    get() = _postsToReparseFlow.asSharedFlow()

  private val alreadyLoaded = ConcurrentHashMap<ChanDescriptor, Unit>(128)
  private val oldPostHidesCleared = AtomicBoolean(false)

  override suspend fun postHidesForThread(threadDescriptor: ThreadDescriptor): Map<PostDescriptor, ChanPostHide> {
    if (alreadyLoaded.putIfAbsent(threadDescriptor, Unit) == null) {
      val postHideMap = postHideLocalSource.postHidesForThread(threadDescriptor)
      if (postHideMap.isNotEmpty()) {
        mutex.withLock {
          val innerSet = hiddenPosts.getOrPut(
            key = threadDescriptor,
            defaultValue = { mutableSetWithCap(32) }
          )

          postHideMap.entries.forEach { (postDescriptor, chanPostHide) ->
            innerSet += postDescriptor
            postHides[postDescriptor] = chanPostHide
          }
        }
      }
    }

    return mutex.withLock {
      val postHides = hiddenPosts[threadDescriptor]
        ?.mapNotNull { postDescriptor -> postHides[postDescriptor] }
        ?.sortedBy { chanPostHide -> chanPostHide.postDescriptor }

      if (postHides.isNullOrEmpty()) {
        return@withLock emptyMap()
      }

      val resultMap = linkedMapWithCap<PostDescriptor, ChanPostHide>(postHides.size)
      postHides.forEach { chanPostHide -> resultMap[chanPostHide.postDescriptor] = chanPostHide }

      return@withLock resultMap
    }
  }

  override suspend fun postHidesForCatalog(
    catalogDescriptor: CatalogDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): Map<PostDescriptor, ChanPostHide> {
    if (alreadyLoaded.putIfAbsent(catalogDescriptor, Unit) == null) {
      val postHideMap = postHideLocalSource.postHidesForCatalog(catalogDescriptor, postDescriptors)
      if (postHideMap.isNotEmpty()) {
        mutex.withLock {
          val innerSet = hiddenPosts.getOrPut(
            key = catalogDescriptor,
            defaultValue = { mutableSetWithCap(32) }
          )

          postHideMap.entries.forEach { (postDescriptor, chanPostHide) ->
            innerSet += postDescriptor
            postHides[postDescriptor] = chanPostHide
          }
        }
      }
    }

    return mutex.withLock {
      val postHides = hiddenPosts[catalogDescriptor]
        ?.mapNotNull { postDescriptor -> postHides[postDescriptor] }
        ?.sortedBy { chanPostHide -> chanPostHide.postDescriptor }

      if (postHides.isNullOrEmpty()) {
        return@withLock emptyMap()
      }

      val resultMap = linkedMapWithCap<PostDescriptor, ChanPostHide>(postHides.size)
      postHides.forEach { chanPostHide -> resultMap[chanPostHide.postDescriptor] = chanPostHide }

      return@withLock resultMap
    }
  }

  override suspend fun postHideForPostDescriptor(postDescriptor: PostDescriptor): ChanPostHide? {
    return mutex.withLock { postHides[postDescriptor] }
  }

  override suspend fun isPostHidden(postDescriptor: PostDescriptor): Boolean {
    return mutex.withLock { postHides[postDescriptor]?.isHidden() ?: false }
  }

  override suspend fun createOrUpdate(chanDescriptor: ChanDescriptor, chanPostHides: Collection<ChanPostHide>): Result<Unit> {
    val localSourceInsertResult = postHideLocalSource.createOrUpdate(chanDescriptor, chanPostHides)
    if (localSourceInsertResult.isFailure) {
      return localSourceInsertResult
    }

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

    return Result.success(Unit)
  }

  override suspend fun update(postDescriptor: PostDescriptor, updater: (ChanPostHide) -> ChanPostHide): Result<Unit> {
    return update(listOf(postDescriptor), updater)
  }

  override suspend fun update(
    postDescriptors: Collection<PostDescriptor>,
    updater: (ChanPostHide) -> ChanPostHide
  ): Result<Unit> {
    val newChanPostHides = postDescriptors.mapNotNull { postDescriptor ->
      val prevChanPostHide = postHides[postDescriptor]
        ?.copy()
        ?: return@mapNotNull null

      val newChanPostHide = updater(prevChanPostHide)

      if (prevChanPostHide == newChanPostHide) {
        return@mapNotNull null
      }

      return@mapNotNull newChanPostHide
    }

    if (newChanPostHides.isEmpty()) {
      return Result.success(Unit)
    }

    val localSourceInsertResult = postHideLocalSource.update(newChanPostHides)
    if (localSourceInsertResult.isFailure) {
      return localSourceInsertResult
    }

    mutex.withLock {
      newChanPostHides.forEach { newChanPostHide ->
        postHides[newChanPostHide.postDescriptor] = newChanPostHide
      }
    }

    if (newChanPostHides.isNotEmpty()) {
      _postsToReparseFlow.emit(newChanPostHides.map { it.postDescriptor })
    }

    return Result.success(Unit)
  }

  override suspend fun delete(postDescriptor: PostDescriptor): Result<Unit> {
    return delete(listOf(postDescriptor))
  }

  override suspend fun delete(
    postDescriptors: Collection<PostDescriptor>,
  ): Result<Unit> {
    val localSourceDeleteResult = postHideLocalSource.delete(postDescriptors)
    if (localSourceDeleteResult.isFailure) {
      return localSourceDeleteResult
    }

    val toEmit = mutex.withLock {
      val toEmit = mutableListOf<PostDescriptor>()

      for (postDescriptor in postDescriptors) {
        val prevChanPostHide = postHides.remove(postDescriptor)
          ?: continue

        toEmit += prevChanPostHide.postDescriptor
      }

      return@withLock toEmit
    }

    if (toEmit.isNotEmpty()) {
      _postsToReparseFlow.emit(toEmit)
    }

    return Result.success(Unit)
  }

  override suspend fun deleteOlderThanThreeMonths(): Result<Int> {
    if (!oldPostHidesCleared.compareAndSet(false, true)) {
      return Result.success(-1)
    }

    return postHideLocalSource.deleteOlderThanThreeMonths()
  }

}