package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import logcat.logcat

class CatalogScreenToolbarActionHandler(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val appSettings: AppSettings,
  private val catalogScreenViewModel: CatalogScreenViewModel,
  private val threadScreenViewModel: ThreadScreenViewModel,
  private val homeScreenViewModel: HomeScreenViewModel,
  private val snackbarManager: SnackbarManager
) {

  suspend fun processClickedToolbarMenuItem(menuItem: FloatingMenuItem) {
    logcat { "catalog processClickedToolbarMenuItem id=${menuItem.menuItemKey}" }

    when (menuItem.menuItemKey) {
      ACTION_RELOAD -> catalogScreenViewModel.reload()
      ACTION_LAYOUT_MODE -> handleLayoutMode()
      ACTION_BOOKMARKS_SCREEN_POSITION -> appSettings.bookmarksScreenOnLeftSide.toggle()
      ACTION_OPEN_THREAD_BY_IDENTIFIER -> handleOpenThreadByIdentifier()
      ACTION_SCROLL_TOP -> catalogScreenViewModel.scrollTop()
      ACTION_SCROLL_BOTTOM -> catalogScreenViewModel.scrollBottom()
    }
  }

  private suspend fun handleLayoutMode() {
    val floatingMenuItems = mutableListOf<FloatingMenuItem>()

    floatingMenuItems += FloatingMenuItem.Group(
      checkedMenuItemKey = appSettings.layoutType.read(),
      groupItems = LayoutType.values().map { layoutType ->
        FloatingMenuItem.Text(
          menuItemKey = layoutType,
          text = FloatingMenuItem.MenuItemText.String(layoutType.name)
        )
      }
    )

    navigationRouter.presentScreen(
      FloatingMenuScreen(
        floatingMenuKey = FloatingMenuScreen.CATALOG_OVERFLOW_LAYOUT_TYPE,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        menuItems = floatingMenuItems,
        onMenuItemClicked = { clickedMenuItem ->
          if (clickedMenuItem.menuItemKey is LayoutType) {
            val layoutType = (clickedMenuItem.menuItemKey as LayoutType)
            appSettings.layoutType.write(layoutType)
          }
        }
      )
    )
  }

  private fun handleOpenThreadByIdentifier() {
    navigationRouter.presentScreen(
      DialogScreen(
        dialogKey = DialogScreen.CATALOG_OVERFLOW_OPEN_THREAD_BY_IDENTIFIER,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        params = DialogScreen.Params(
          title = DialogScreen.Text.Id(R.string.catalog_screen_enter_identifier),
          description = DialogScreen.Text.Id(R.string.catalog_screen_enter_identifier_description),
          input = DialogScreen.Input.String(),
          negativeButton = DialogScreen.DialogButton(buttonText = R.string.cancel),
          positiveButton = DialogScreen.PositiveDialogButton(
            buttonText = R.string.ok,
            onClick = { value ->
              if (value.isNullOrEmpty()) {
                return@PositiveDialogButton
              }

              val resolvedDescriptor = homeScreenViewModel.resolveDescriptorFromRawIdentifier(value)
              if (resolvedDescriptor == null) {
                snackbarManager.toast("Failed to parse \'$value'\"")
                return@PositiveDialogButton
              }

              // TODO(KurobaEx): come up with a better solution than doing it manually
              homeScreenViewModel.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)

              when (resolvedDescriptor) {
                is ResolvedDescriptor.CatalogOrThread -> {
                  when (resolvedDescriptor.chanDescriptor) {
                    is CatalogDescriptor -> {
                      catalogScreenViewModel.loadCatalog(resolvedDescriptor.chanDescriptor)
                    }
                    is ThreadDescriptor -> {
                      threadScreenViewModel.loadThread(resolvedDescriptor.chanDescriptor)
                    }
                  }
                }
                is ResolvedDescriptor.Post -> {
                  threadScreenViewModel.loadThread(resolvedDescriptor.postDescriptor.threadDescriptor)
                  // TODO(KurobaEx): do something with the postNo (mark it or something)
                }
              }
            }
          )
        )
      )
    )
  }

  companion object {
    const val ACTION_OPEN_THREAD_BY_IDENTIFIER = 0
    const val ACTION_RELOAD = 1
    const val ACTION_SCROLL_TOP = 2
    const val ACTION_SCROLL_BOTTOM = 3
    const val ACTION_LAYOUT_MODE = 4
    const val ACTION_BOOKMARKS_SCREEN_POSITION = 5
  }

}