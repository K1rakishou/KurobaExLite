package com.github.k1rakishou.kurobaexlite.features.posts.thread

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class ThreadScreenToolbarActionHandler(
  componentActivity: ComponentActivity
) {
  private val siteManager: SiteManager by inject(SiteManager::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  private val threadScreenViewModel by componentActivity.viewModels<ThreadScreenViewModel>()

  fun processClickedToolbarMenuItem(
    menuItem: FloatingMenuItem,
  ) {
    logcat { "thread processClickedToolbarMenuItem id=${menuItem.menuItemKey}" }

    when (menuItem.menuItemKey) {
      ACTION_RELOAD -> {
        threadScreenViewModel.reload(PostScreenViewModel.LoadOptions(deleteCached = true))
      }
      ACTION_COPY_THREAD_URL -> {
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return
        val site = siteManager.bySiteKey(threadDescriptor.siteKey)
          ?: return
        val threadUrl = site.desktopUrl(
          threadDescriptor = threadDescriptor,
          postNo = null,
          postSubNo = null
        ) ?: return

        androidHelpers.setClipboardContent(label = "thread url", content = threadUrl)
        snackbarManager.toast(
          screenKey = ThreadScreen.SCREEN_KEY,
          message = "Thread url copied to clipboard"
        )
      }
      ACTION_SCROLL_TOP -> threadScreenViewModel.scrollTop()
      ACTION_SCROLL_BOTTOM -> threadScreenViewModel.scrollBottom()
    }
  }

  companion object {
    const val ACTION_RELOAD = 0
    const val ACTION_COPY_THREAD_URL = 1

    const val ACTION_SCROLL_TOP = 100
    const val ACTION_SCROLL_BOTTOM = 101
  }

}