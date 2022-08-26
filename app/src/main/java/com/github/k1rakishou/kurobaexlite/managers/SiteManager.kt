package com.github.k1rakishou.kurobaexlite.managers

import android.content.Context
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SiteManager(
  private val appContext: Context
) : ISiteManager {
  private val sites = ConcurrentHashMap<SiteKey, Site>()

  init {
    sites[Chan4.SITE_KEY] = Chan4(appContext)
  }

  override fun byUrl(httpUrl: HttpUrl): Site? {
    return sites.values.firstOrNull { site -> site.matchesUrl(httpUrl) }
  }

  override fun bySiteKey(siteKey: SiteKey): Site? {
    return sites[siteKey]
  }

  override fun supportsSite(siteKey: SiteKey): Boolean {
    return sites.containsKey(siteKey)
  }

  override fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    val httpUrl = rawIdentifier.toHttpUrlOrNull()

    for ((_, site) in sites) {
      if (httpUrl != null) {
        val resolvedDescriptor = site.resolveDescriptorFromUrl(httpUrl)
        if (resolvedDescriptor != null) {
          return resolvedDescriptor
        }
      }

      // TODO(KurobaEx): add more stuff
    }

    return null
  }

}