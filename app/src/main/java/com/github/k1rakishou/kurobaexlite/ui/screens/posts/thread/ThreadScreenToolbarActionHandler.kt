package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.album.AlbumScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class ThreadScreenToolbarActionHandler {
  private val siteManager: SiteManager by inject(SiteManager::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  private lateinit var threadScreenViewModel: ThreadScreenViewModel

  fun processClickedToolbarMenuItem(
    componentActivity: ComponentActivity,
    navigationRouter: NavigationRouter,
    menuItem: FloatingMenuItem,
  ) {
    logcat { "thread processClickedToolbarMenuItem id=${menuItem.menuItemKey}" }
    threadScreenViewModel = componentActivity.viewModels<ThreadScreenViewModel>().value

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
        snackbarManager.toast("Thread url copied to clipboard")
      }
      ACTION_THREAD_ALBUM -> {
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return

        val childRouter = navigationRouter.childRouter(AlbumScreen.SCREEN_KEY)
        navigationRouter.pushScreen(AlbumScreen(threadDescriptor, componentActivity, childRouter))
      }
      ACTION_SCROLL_TOP -> threadScreenViewModel.scrollTop()
      ACTION_SCROLL_BOTTOM -> threadScreenViewModel.scrollBottom()
    }
  }

  companion object {
    const val ACTION_RELOAD = 0
    const val ACTION_COPY_THREAD_URL = 1
    const val ACTION_THREAD_ALBUM = 2

    const val ACTION_SCROLL_TOP = 100
    const val ACTION_SCROLL_BOTTOM = 101
  }

}