package com.github.k1rakishou.kurobaexlite.sites

import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

sealed class ResolvedDescriptor {
  data class CatalogOrThread(val chanDescriptor: ChanDescriptor) : ResolvedDescriptor()
  data class Post(val postDescriptor: PostDescriptor) : ResolvedDescriptor()
}