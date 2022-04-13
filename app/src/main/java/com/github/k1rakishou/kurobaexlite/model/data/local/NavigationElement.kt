package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

sealed class NavigationElement {
  abstract val chanDescriptor: ChanDescriptor

  val elementTitle: String?
    get() {
      return when (this) {
        is Catalog -> null
        is Thread -> this.title
      }
    }

  val elementIconUrl: String?
    get() {
      return when (this) {
        is Catalog -> null
        is Thread -> this.iconUrl
      }
    }

  class Catalog(
    override val chanDescriptor: CatalogDescriptor
  ) : NavigationElement() {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Catalog

      if (chanDescriptor != other.chanDescriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return chanDescriptor.hashCode()
    }

  }

  class Thread(
    override val chanDescriptor: ThreadDescriptor,
    val title: String?,
    val iconUrl: String?
  ) : NavigationElement() {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Thread

      if (chanDescriptor != other.chanDescriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return chanDescriptor.hashCode()
    }

  }
}