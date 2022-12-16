package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.repository.CatalogPagesRepository
import com.github.k1rakishou.kurobaexlite.model.repository.GlobalSearchRepository
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.model.source.dvach.DvachDataSource
import org.koin.core.module.Module

internal fun Module.model() {
  single { Chan4DataSource(siteManager = get(), kurobaOkHttpClient = get(), moshi = get()) }
  single { DvachDataSource(siteManager = get(), kurobaOkHttpClient = get(), moshi = get(), loadChanCatalog = get()) }

  single { ChanCache(androidHelpers = get()) }

  single {
    CatalogPagesRepository(
      appScope = get(),
      siteManager = get(),
    )
  }
  single { GlobalSearchRepository(siteManager = get()) }
}