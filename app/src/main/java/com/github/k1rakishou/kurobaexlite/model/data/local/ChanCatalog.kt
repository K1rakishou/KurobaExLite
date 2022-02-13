package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat

class ChanCatalog {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val threads = mutableListOf<OriginalPostData>()
  @GuardedBy("mutex")
  private val threadsMap = mutableMapOf<PostDescriptor, OriginalPostData>()

  suspend fun insert(postDataCollection: Collection<PostData>) {
    mutex.withLock {
      for (postData in postDataCollection) {
        check(postData is OriginalPostData) { "postData is not OriginalPostData" }
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

  suspend fun getPostDataList(): Collection<PostData> {
    return mutex.withLock { threads.toList() }
  }

  companion object {
    private const val TAG = "ChanCatalog"
  }
}