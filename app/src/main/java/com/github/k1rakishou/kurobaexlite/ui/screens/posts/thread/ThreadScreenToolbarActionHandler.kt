package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import logcat.logcat

class ThreadScreenToolbarActionHandler(
  private val threadScreenViewModel: ThreadScreenViewModel
) {

  fun processClickedToolbarMenuItem(menuItem: FloatingMenuItem) {
    logcat { "thread processClickedToolbarMenuItem id=${menuItem.menuItemKey}" }

    when (menuItem.menuItemKey) {
      ACTION_RELOAD -> threadScreenViewModel.reload()
      ACTION_SCROLL_TOP -> threadScreenViewModel.scrollTop()
      ACTION_SCROLL_BOTTOM -> threadScreenViewModel.scrollBottom()
    }
  }

  companion object {
    const val ACTION_RELOAD = 0
    const val ACTION_SCROLL_TOP = 1
    const val ACTION_SCROLL_BOTTOM = 2
  }

}