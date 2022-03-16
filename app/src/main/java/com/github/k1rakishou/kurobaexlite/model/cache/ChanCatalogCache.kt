package com.github.k1rakishou.kurobaexlite.model.cache

import android.os.SystemClock
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
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

  suspend fun insert(postDataCollection: Collection<PostData>) {
    mutex.withLockNonCancellable {
      for (postData in postDataCollection) {
        check(postData is OriginalPostData) { "postData is not OriginalPostData" }

        if (postData.parsedPostDataRead == null) {
          error("parsedPostDataRead is null")
        }
      }

      threads.clear()
      threadsMap.clear()

      val originalPostDataCollection = postDataCollection
        .map { postData -> postData as OriginalPostData }

      threads.addAll(originalPostDataCollection)
      originalPostDataCollection.forEach { originalPostData ->
        threadsMap[originalPostData.postDescriptor] = originalPostData
      }

      logcat(tag = TAG) { "insert() ${postDataCollection.size} threads inserted or updated" }
    }
  }

  suspend fun getPostDataList(): List<PostData> {
    return mutex.withLockNonCancellable { threads.toList() }
  }

  companion object {
    private const val TAG = "ChanCatalog"
  }
}