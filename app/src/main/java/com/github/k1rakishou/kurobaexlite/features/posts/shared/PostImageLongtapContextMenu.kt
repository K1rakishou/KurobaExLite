package com.github.k1rakishou.kurobaexlite.features.posts.shared

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.java.KoinJavaComponent.inject

class PostImageLongtapContextMenu(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val screenCoroutineScope: CoroutineScope
) {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  fun showMenu(
    postImage: IPostImage,
    viewProvider: () -> View,
    onAlbumScreenToggleSelection: (() -> Unit)? = null
  ) {
    screenCoroutineScope.launch {
      val floatingMenuItems = mutableListOf<FloatingMenuItem>().apply {
        this += FloatingMenuItem.Text(
          menuItemKey = COPY_IMAGE_FULL_URL,
          menuItemData = postImage.ownerPostDescriptor,
          text = FloatingMenuItem.MenuItemText.Id(R.string.copy_image_full_url)
        )

        if (onAlbumScreenToggleSelection != null) {
          this += FloatingMenuItem.Text(
            menuItemKey = ALBUM_SCREEN_START_SELECTION_MODE,
            menuItemData = postImage.ownerPostDescriptor,
            text = FloatingMenuItem.MenuItemText.Id(R.string.album_screen_toggle_selection)
          )
        }
      }

      if (floatingMenuItems.isEmpty()) {
        return@launch
      }

      viewProvider().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

      val selectedMenuItem = suspendCancellableCoroutine<FloatingMenuItem?> { cancellableContinuation ->
        var selectedMenuItem: FloatingMenuItem? = null

        navigationRouter.presentScreen(
          FloatingMenuScreen(
            floatingMenuKey = FloatingMenuScreen.POST_IMAGE_LONGTAP_MENU,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            menuItems = floatingMenuItems,
            onMenuItemClicked = { menuItem -> selectedMenuItem = menuItem },
            onDismiss = { cancellableContinuation.resumeSafe(selectedMenuItem) }
          )
        )
      }

      if (selectedMenuItem == null) {
        return@launch
      }

      when (selectedMenuItem.menuItemKey as Int) {
        COPY_IMAGE_FULL_URL -> {
          androidHelpers.copyToClipboard("Post image url", postImage.fullImageAsString)
        }
        ALBUM_SCREEN_START_SELECTION_MODE -> {
          onAlbumScreenToggleSelection?.invoke()
        }
      }
    }
  }

  companion object {
    private const val COPY_IMAGE_FULL_URL = 0
    private const val ALBUM_SCREEN_START_SELECTION_MODE = 1
  }

}