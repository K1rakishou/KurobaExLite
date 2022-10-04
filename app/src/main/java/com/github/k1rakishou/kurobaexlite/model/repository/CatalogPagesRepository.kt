package com.github.k1rakishou.kurobaexlite.model.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat
import org.joda.time.DateTime
import org.joda.time.Duration

class CatalogPagesRepository(
  private val appScope: CoroutineScope,
  private val siteManager: SiteManager,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val pagesCache = mutableMapOf<CatalogDescriptor, CachedCatalogPagesData>()
  @GuardedBy("mutex")
  private val activeUpdates = mutableMapOf<CatalogDescriptor, Job>()

  private val _catalogPagesUpdatedFlow = MutableSharedFlow<CatalogDescriptor>(extraBufferCapacity = Channel.UNLIMITED)
  val catalogPagesUpdatedFlow: SharedFlow<CatalogDescriptor>
    get() = _catalogPagesUpdatedFlow.asSharedFlow()

  suspend fun getThreadPage(threadDescriptor: ThreadDescriptor): ThreadPage? {
    val catalogDescriptor = threadDescriptor.catalogDescriptor
    val siteKey = threadDescriptor.catalogDescriptor.siteKey

    val (threadPageFromCache, needUpdatePages) = mutex.withLock {
      val cachedCatalogPagesData = pagesCache[catalogDescriptor]
      val now = DateTime.now()
      var needUpdatePages = false

      if (cachedCatalogPagesData == null || now > cachedCatalogPagesData.nextUpdateTime) {
        needUpdatePages = true
      }

      val threadPage = cachedCatalogPagesData
        ?.catalogPagesData
        ?.let { catalogPagesData ->
          val page = catalogPagesData.pagesInfo[threadDescriptor]
            ?: return@let null

          return@let ThreadPage(
            page = page,
            totalPages = catalogPagesData.pagesTotal
          )
        }

      return@withLock threadPage to needUpdatePages
    }

    val job = appScope.launch(context = dispatcher, start = CoroutineStart.LAZY) {
      try {
        val catalogPagesData = updateCatalogPages(catalogDescriptor)

        mutex.withLock {
          if (catalogPagesData != null) {
            pagesCache[catalogDescriptor] = CachedCatalogPagesData(
              catalogPagesData = catalogPagesData,
              nextUpdateTime = DateTime.now() + normalUpdateInterval
            )
          } else {
            pagesCache[catalogDescriptor]?.let { cachedCatalogPagesData ->
              cachedCatalogPagesData.nextUpdateTime = DateTime.now() + errorUpdateInternal
            }
          }
        }

        if (catalogPagesData != null) {
          _catalogPagesUpdatedFlow.emit(catalogDescriptor)
        }
      } finally {
        mutex.withLock { activeUpdates.remove(catalogDescriptor) }
      }
    }

    mutex.withLock {
      if (
        needUpdatePages &&
        !activeUpdates.containsKey(catalogDescriptor) &&
        siteManager.supportsSite(siteKey)
      ) {
        activeUpdates[catalogDescriptor] = job
        job.start()
      } else {
        job.cancel()
      }
    }

    return threadPageFromCache
  }

  private suspend fun updateCatalogPages(catalogDescriptor: CatalogDescriptor): CatalogPagesData? {
    val site = siteManager.bySiteKey(catalogDescriptor.siteKey)
    if (site == null) {
      logcatError(TAG) { "Site ${catalogDescriptor.siteKeyActual} is not supported" }
      return null
    }

    val catalogPagesInfo = site.catalogPagesInfo()
    if (catalogPagesInfo == null) {
      logcatError(TAG) { "Site ${catalogDescriptor.siteKeyActual} does not support fetching catalog page info" }
      return null
    }

    logcat(TAG) { "loading catalog page info for ${catalogDescriptor}" }

    val catalogPagesData = catalogPagesInfo
      .catalogPagesDataSource()
      .loadCatalogPagesData(catalogDescriptor)
      .onFailure { error ->
        logcatError(TAG) {
          "loadCatalogPagesData($catalogDescriptor) error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .onSuccess { data ->
        if (data != null) {
          logcat(TAG) { "loaded ${data.pagesInfo.size} threads with page info for ${catalogDescriptor}" }
        }
      }
      .getOrNull()

    return catalogPagesData
  }

  data class ThreadPage(
    val page: Int,
    val totalPages: Int
  )

  class CachedCatalogPagesData(
    val catalogPagesData: CatalogPagesData,
    var nextUpdateTime: DateTime
  )

  companion object {
    private const val TAG = "CatalogPagesRepository"

    private val normalUpdateInterval = Duration.standardMinutes(5)
    private val errorUpdateInternal = Duration.standardSeconds(10)
  }
}