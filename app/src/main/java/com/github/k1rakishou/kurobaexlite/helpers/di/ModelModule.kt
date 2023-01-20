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
import com.github.k1rakishou.kurobaexlite.model.source.local.IPostHideLocalSource
import com.github.k1rakishou.kurobaexlite.model.source.local.PostHideLocalSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.dvach.DvachDataSource
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
  single<IPostHideLocalSource> { PostHideLocalSource(kurobaExLiteDatabase = get()) }
  single<IPostHideRepository> { PostHideRepository(postHideLocalSource = get()) }
  single<IPostReplyChainRepository> { PostReplyChainRepository() }
  single {
    ParsedPostDataRepository(
      coroutineScope = get(),
      appSettings = get(),
      appResources = get(),
      postCommentParser = get(),
      postCommentApplier = get(),
      postReplyChainRepository = get(),
      markedPostManager = get()
    )
  }
}