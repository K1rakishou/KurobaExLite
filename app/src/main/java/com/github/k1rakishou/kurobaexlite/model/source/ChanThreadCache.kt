package com.github.k1rakishou.kurobaexlite.model.source

import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThread
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.concurrent.ConcurrentHashMap

class ChanThreadCache {
  private val catalogs = ConcurrentHashMap<CatalogDescriptor, ChanCatalog>(128)
  private val threads = ConcurrentHashMap<ThreadDescriptor, ChanThread>(128)

  suspend fun insertCatalogThreads(
    catalogDescriptor: CatalogDescriptor,
    catalogThreads: Collection<PostData>
  ) {
    val chanCatalog = catalogs.getOrPut(
      key = catalogDescriptor,
      defaultValue = { ChanCatalog() }
    )

    chanCatalog.insert(catalogThreads)
  }

  suspend fun insertThreadPosts(
    threadDescriptor: ThreadDescriptor,
    threadPosts: Collection<PostData>
  ) {
    val chanThread = threads.getOrPut(key = threadDescriptor, defaultValue = { ChanThread() })
    chanThread.insert(threadPosts)
  }

  suspend fun getManyForDescriptor(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ): List<PostData> {
    val resultList = mutableListWithCap<PostData>(postDescriptors.size)

    when (chanDescriptor) {
      is CatalogDescriptor -> {
        val chanCatalog = catalogs[chanDescriptor]
        if (chanCatalog != null) {
          resultList.addAll(chanCatalog.getPostDataList())
        }
      }
      is ThreadDescriptor -> {
        val threadPosts = threads[chanDescriptor]?.getMany(postDescriptors)
        if (threadPosts.isNotNullNorEmpty()) {
          resultList.addAll(threadPosts)
        }
      }
    }

    return resultList
  }

  suspend fun getThreadPosts(threadDescriptor: ThreadDescriptor): List<PostData> {
    return threads[threadDescriptor]?.getAll() ?: emptyList()
  }

}