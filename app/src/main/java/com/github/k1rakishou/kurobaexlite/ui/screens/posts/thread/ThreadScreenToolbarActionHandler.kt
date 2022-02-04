package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import logcat.logcat

class ThreadScreenToolbarActionHandler(
  private val threadScreenViewModel: ThreadScreenViewModel
) {

  fun processClickedToolbarMenuItem(menuItem: ToolbarMenuItem) {
    logcat { "thread processClickedToolbarMenuItem id=${menuItem.menuItemId}" }

    when (menuItem.menuItemId) {
      ACTION_RELOAD -> threadScreenViewModel.reload()
    }
  }

  companion object {
    const val ACTION_RELOAD = 0
  }

}