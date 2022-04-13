package com.github.k1rakishou.kurobaexlite.model.data.ui

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

@Immutable
sealed class UiNavigationElement {
  val key: Any
    get() = chanDescriptor

  abstract val chanDescriptor: ChanDescriptor

  data class Catalog(
    override val chanDescriptor: CatalogDescriptor,
    val iconUrl: String?
  ) : UiNavigationElement()

  data class Thread(
    override val chanDescriptor: ThreadDescriptor,
    val title: String?,
    val iconUrl: String?
  ) : UiNavigationElement()

}