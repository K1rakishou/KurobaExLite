package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.kurobaexlite.helpers.html.StaticHtmlColorRepository
import com.github.k1rakishou.kurobaexlite.model.cache.ChanPostCache
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.repository.CatalogPagesRepository
import com.github.k1rakishou.kurobaexlite.model.repository.GlobalSearchRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository
import com.github.k1rakishou.kurobaexlite.model.repository.PostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.PostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.model.source.dvach.DvachDataSource
import org.koin.core.module.Module

internal fun Module.model() {
  single {
    Chan4DataSource(
      siteManager = get(),
      kurobaOkHttpClient = get(),
      moshi = get()
    )
  }
  single {
    DvachDataSource(
      siteManager = get(),
      kurobaOkHttpClient = get(),
      moshi = get(),
      loadChanCatalog = get()
    )
  }

  single<IChanPostCache> { ChanPostCache(androidHelpers = get()) }

  single {
    CatalogPagesRepository(
      appScope = get(),
      siteManager = get(),
    )
  }
  single { GlobalSearchRepository(siteManager = get()) }
  single { StaticHtmlColorRepository() }
  single<IPostHideRepository> { PostHideRepository() }
  single<IPostReplyChainRepository> { PostReplyChainRepository() }
  single {
    ParsedPostDataRepository(
      appContext = get(),
      coroutineScope = get(),
      appSettings = get(),
      postCommentParser = get(),
      postCommentApplier = get(),
      postReplyChainRepository = get(),
      markedPostManager = get()
    )
  }
}