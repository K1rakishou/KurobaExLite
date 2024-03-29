package com.github.k1rakishou.kurobaexlite.model.cache

import android.os.SystemClock
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.util.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.util.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.sync.Mutex
import logcat.logcat

class ChanCatalogCache(
  val catalogDescriptor: CatalogDescriptor
) : IChanCache {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val threads = mutableListOf<OriginalPostData>()
  @GuardedBy("mutex")
  private val threadsMap = mutableMapOf<PostDescriptor, OriginalPostData>()

  @Volatile
  override var lastUpdateTime: Long = SystemClock.elapsedRealtime()
    private set

  override val chanDescriptor: ChanDescriptor = catalogDescriptor

  override suspend fun hasPosts(): Boolean {
    return mutex.withLockNonCancellable { threads.isNotEmpty() }
  }

  suspend fun onCatalogAccessed() {
    mutex.withLockNonCancellable { lastUpdateTime = SystemClock.elapsedRealtime() }
  }

  suspend fun insert(postCellDataCollection: Collection<IPostData>) {
    BackgroundUtils.ensureBackgroundThread()

    mutex.withLockNonCancellable {
      for (postData in postCellDataCollection) {
        check(postData is OriginalPostData) { "postData is not OriginalPostData" }
      }

      threads.clear()
      threadsMap.clear()

      val originalPostDataCollection = postCellDataCollection
        .map { postData -> postData as OriginalPostData }

      threads.addAll(originalPostDataCollection)
      originalPostDataCollection.forEach { originalPostData ->
        threadsMap[originalPostData.postDescriptor] = originalPostData
      }

      logcat(tag = TAG) { "insert() ${postCellDataCollection.size} threads inserted or updated" }
    }
  }

  suspend fun getMany(postDescriptors: Collection<PostDescriptor>): List<IPostData> {
    return mutex.withLockNonCancellable {
      postDescriptors.mapNotNull { postDescriptor -> threadsMap[postDescriptor] }
    }
  }

  suspend fun getPostDataList(): List<OriginalPostData> {
    return mutex.withLockNonCancellable { threads.toList() }
  }

  suspend fun getPostDescriptorList(): List<PostDescriptor> {
    return mutex.withLockNonCancellable { threads.map { it.postDescriptor } }
  }

  suspend fun getOriginalPost(postDescriptor: PostDescriptor): OriginalPostData? {
    return mutex.withLockNonCancellable { threadsMap[postDescriptor] }
  }

  companion object {
    private const val TAG = "ChanCatalogCache"
  }
}