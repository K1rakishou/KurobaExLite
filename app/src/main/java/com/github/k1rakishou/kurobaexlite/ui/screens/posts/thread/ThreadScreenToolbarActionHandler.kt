package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import logcat.logcat

class ThreadScreenToolbarActionHandler(
  private val siteManager: SiteManager,
  private val snackbarManager: SnackbarManager,
  private val androidHelpers: AndroidHelpers
) {

  fun processClickedToolbarMenuItem(
    menuItem: FloatingMenuItem,
    threadScreenViewModelProvider: () -> ThreadScreenViewModel
  ) {
    logcat { "thread processClickedToolbarMenuItem id=${menuItem.menuItemKey}" }

    when (menuItem.menuItemKey) {
      ACTION_RELOAD -> {
        threadScreenViewModelProvider().reload(PostScreenViewModel.LoadOptions(deleteCached = true))
      }
      ACTION_COPY_THREAD_URL -> {
        val threadDescriptor = threadScreenViewModelProvider().threadDescriptor
          ?: return
        val site = siteManager.bySiteKey(threadDescriptor.siteKey)
          ?: return
        val threadUrl = site.desktopUrl(
          threadDescriptor = threadDescriptor,
          postNo = null,
          postSubNo = null
        ) ?: return

        androidHelpers.setClipboardContent(label = "thread url", content = threadUrl)
        snackbarManager.toast("Thread url copied to clipboard")
      }
      ACTION_SCROLL_TOP -> threadScreenViewModelProvider().scrollTop()
      ACTION_SCROLL_BOTTOM -> threadScreenViewModelProvider().scrollBottom()
    }
  }

  companion object {
    const val ACTION_RELOAD = 0
    const val ACTION_COPY_THREAD_URL = 1

    const val ACTION_SCROLL_TOP = 100
    const val ACTION_SCROLL_BOTTOM = 101
  }

}