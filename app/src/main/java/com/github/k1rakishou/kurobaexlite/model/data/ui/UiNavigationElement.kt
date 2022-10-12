package com.github.k1rakishou.kurobaexlite.model.data.ui

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

@Immutable
sealed class UiNavigationElement {
  abstract fun matchesQuery(text: String): Boolean

  val key: Any
    get() = chanDescriptor

  abstract val chanDescriptor: ChanDescriptor
  abstract val iconUrl: String?

  data class Catalog(
    override val chanDescriptor: CatalogDescriptor,
    override val iconUrl: String?
  ) : UiNavigationElement() {

    override fun matchesQuery(text: String): Boolean {
      return chanDescriptor.asReadableString().contains(text, ignoreCase = true)
    }

  }

  data class Thread(
    override val chanDescriptor: ThreadDescriptor,
    val title: String?,
    override val iconUrl: String?
  ) : UiNavigationElement() {

    override fun matchesQuery(text: String): Boolean {
      return title?.contains(text, ignoreCase = true) ?: false
    }

  }

}