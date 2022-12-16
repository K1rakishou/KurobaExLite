package com.github.k1rakishou.kurobaexlite.managers

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.model.source.dvach.DvachDataSource
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import com.github.k1rakishou.kurobaexlite.sites.dvach.Dvach
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.java.KoinJavaComponent.inject

class SiteManager(
  private val siteProvider: SiteProvider
) : ISiteManager {
  val sites by lazy { siteProvider.getAllSites() }

  override fun byUrl(httpUrl: HttpUrl): Site? {
    return sites.values.firstOrNull { site -> site.matchesUrl(httpUrl) }
  }

  override fun bySiteKey(siteKey: SiteKey): Site? {
    return sites[siteKey]
  }

  override fun bySiteKeyOrDefault(siteKey: SiteKey): Site {
    return sites[siteKey] ?: sites[Chan4.SITE_KEY]!!
  }

  override fun supportsSite(siteKey: SiteKey): Boolean {
    return sites.containsKey(siteKey)
  }

  override fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    var actualIdentifier = rawIdentifier

    val methodSeparator = "://"
    val indexOfMethodSeparator = actualIdentifier.indexOf(methodSeparator)

    if (indexOfMethodSeparator >= 0) {
      actualIdentifier = actualIdentifier.substring(indexOfMethodSeparator + methodSeparator.length)
    }

    actualIdentifier = "https://${actualIdentifier}"

    val httpUrl = actualIdentifier.toHttpUrlOrNull()
      ?: return null

    for ((_, site) in sites) {
      if (!site.matchesUrl(httpUrl)) {
        continue
      }

      val resolvedDescriptor = site.resolveDescriptorFromUrl(httpUrl)
      if (resolvedDescriptor != null) {
        return resolvedDescriptor
      }
    }

    return null
  }

  inline fun iterateSites(iterator: (Site) -> Unit) {
    sites.entries.forEach { (_, site) -> iterator(site) }
  }

}

class SiteProvider(
  private val appContext: Context,
  private val appScope: CoroutineScope
) {
  private val chan4DataSource by inject<Chan4DataSource>(Chan4DataSource::class.java)
  private val dvachDataSource by inject<DvachDataSource>(DvachDataSource::class.java)
  private val appSettings by inject<AppSettings>(AppSettings::class.java)
  private val moshi by inject<Moshi>(Moshi::class.java)
  private val proxiedOkHttpClient by inject<ProxiedOkHttpClient>(ProxiedOkHttpClient::class.java)
  private val loadChanCatalog by inject<LoadChanCatalog>(LoadChanCatalog::class.java)

  fun getAllSites(): Map<SiteKey, Site> {
    val sites = mutableMapOf<SiteKey, Site>()

    sites[Chan4.SITE_KEY] = Chan4(
      appContext = appContext,
      chan4DataSource = chan4DataSource,
      appSettings = appSettings,
      moshi = moshi,
      proxiedOkHttpClient = proxiedOkHttpClient,
      loadChanCatalog = loadChanCatalog
    )

    sites[Dvach.SITE_KEY] = Dvach(
      appContext = appContext,
      appScope = appScope,
      dvachDataSource = dvachDataSource,
      appSettings = appSettings,
      proxiedOkHttpClient = proxiedOkHttpClient,
      moshi = moshi
    )

    return sites
  }

}