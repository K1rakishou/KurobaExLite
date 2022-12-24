package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.kurobaexlite.helpers.html.StaticHtmlColorRepository
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.model.cache.ChanPostCache
import com.github.k1rakishou.kurobaexlite.model.repository.CatalogPagesRepository
import com.github.k1rakishou.kurobaexlite.model.repository.GlobalSearchRepository
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.model.source.dvach.DvachDataSource
import org.koin.core.module.Module

internal fun Module.model() {
  single {
    Chan4DataSource(
      siteManager = get(),
      kurobaOkHttpClient = get<ProxiedOkHttpClient>(),
      moshi = get()
    )
  }
  single {
    DvachDataSource(
      siteManager = get(),
      kurobaOkHttpClient = get<ProxiedOkHttpClient>(),
      moshi = get(),
      loadChanCatalog = get()
    )
  }

  single<ChanPostCache> { ChanPostCache(androidHelpers = get()) }

  single {
    CatalogPagesRepository(
      appScope = get(),
      siteManager = get(),
    )
  }
  single { GlobalSearchRepository(siteManager = get()) }
  single { StaticHtmlColorRepository() }
}