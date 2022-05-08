package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site

interface ISiteManager {
  fun bySiteKey(siteKey: SiteKey): Site?
  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor?
}