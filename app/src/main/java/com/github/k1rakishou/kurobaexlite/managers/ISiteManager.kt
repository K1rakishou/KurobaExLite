package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import okhttp3.HttpUrl

interface ISiteManager {
  fun bySiteKey(siteKey: SiteKey): Site?
  fun bySiteKeyOrDefault(siteKey: SiteKey): Site
  fun byUrl(httpUrl: HttpUrl): Site?
  fun supportsSite(siteKey: SiteKey): Boolean

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor?
}