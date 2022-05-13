package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.DisposableElement

abstract class KurobaChildToolbar : DisposableElement {
  abstract val toolbarKey: Any

  @Composable
  abstract fun Content()

  override fun onCreate() {

  }

  override fun onDispose() {

  }

  companion object {
    val toolbarIconSize = 30.dp
  }
}