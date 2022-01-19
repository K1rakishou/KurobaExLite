package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.sites.Site
import java.util.concurrent.ConcurrentHashMap

class SiteManager {
  private val sites = ConcurrentHashMap<SiteKey, Site>()

  init {
    sites[Chan4.SITE_KEY] = Chan4()
  }

  fun bySiteKey(siteKey: SiteKey): Site? {
    return sites[siteKey]
  }

}