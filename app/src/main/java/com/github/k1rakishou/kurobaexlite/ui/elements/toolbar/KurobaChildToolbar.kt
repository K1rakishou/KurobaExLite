package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.SaveableComponent
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.DisposableElement

abstract class KurobaChildToolbar : DisposableElement {
  abstract val toolbarKey: String
  abstract val toolbarState: ToolbarState?

  override val elementKey: String
    get() = toolbarKey

  @Composable
  abstract fun Content()

  override fun onCreate() {

  }

  override fun onDispose() {

  }

  interface ToolbarState : SaveableComponent

  companion object {
    val toolbarIconSize = 38.dp
    val toolbarIconPadding = 4.dp
  }
}