package com.github.k1rakishou.kurobaexlite.model.cache

import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor

interface IChanCache {
  val chanDescriptor: ChanDescriptor
  val lastUpdateTime: Long

  suspend fun hasPosts(): Boolean
}