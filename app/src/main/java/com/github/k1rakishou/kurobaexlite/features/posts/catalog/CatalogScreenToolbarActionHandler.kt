package com.github.k1rakishou.kurobaexlite.features.posts.catalog

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class CatalogScreenToolbarActionHandler(
  private val componentActivity: ComponentActivity,
  private val screenCoroutineScope: CoroutineScope
) {
  private val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  private val appSettings: AppSettings by inject(AppSettings::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)

  private lateinit var catalogScreenViewModel: CatalogScreenViewModel
  private lateinit var threadScreenViewModel: ThreadScreenViewModel
  private lateinit var homeScreenViewModel: HomeScreenViewModel

  fun processClickedToolbarMenuItem(
    navigationRouter: NavigationRouter,
    menuItem: FloatingMenuItem
  ) {
    catalogScreenViewModel = componentActivity.viewModels<CatalogScreenViewModel>().value
    threadScreenViewModel = componentActivity.viewModels<ThreadScreenViewModel>().value
    homeScreenViewModel = componentActivity.viewModels<HomeScreenViewModel>().value

    logcat { "catalog processClickedToolbarMenuItem id=${menuItem.menuItemKey}" }

    when (menuItem.menuItemKey) {
      ACTION_RELOAD -> {
        catalogScreenViewModel.reload(PostScreenViewModel.LoadOptions(deleteCached = true))
      }
      ACTION_LAYOUT_MODE -> {
        screenCoroutineScope.launch {
          handleLayoutMode(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter
          )
        }
      }
      ACTION_HISTORY_SCREEN_POSITION -> {
        screenCoroutineScope.launch {
          appSettings.historyScreenOnLeftSide.toggle()
        }
      }
      ACTION_OPEN_THREAD_BY_IDENTIFIER -> {
        handleOpenThreadByIdentifier(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter
        )
      }
      ACTION_CATALOG_ALBUM -> {
        val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
          ?: return

        val albumScreen = AlbumScreen(
          chanDescriptor = catalogDescriptor,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter
        )

        navigationRouter.pushScreen(albumScreen)
      }
      ACTION_SCROLL_TOP -> catalogScreenViewModel.scrollTop()
      ACTION_SCROLL_BOTTOM -> catalogScreenViewModel.scrollBottom()
    }
  }

  private suspend fun handleLayoutMode(
    componentActivity: ComponentActivity,
    navigationRouter: NavigationRouter,
  ) {
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
          screenCoroutineScope.launch {
            if (clickedMenuItem.menuItemKey is LayoutType) {
              val layoutType = (clickedMenuItem.menuItemKey as LayoutType)
              appSettings.layoutType.write(layoutType)
            }
          }
        }
      )
    )
  }

  private fun handleOpenThreadByIdentifier(
    componentActivity: ComponentActivity,
    navigationRouter: NavigationRouter
  ) {
    navigationRouter.presentScreen(
      DialogScreen(
        dialogKey = DialogScreen.CATALOG_OVERFLOW_OPEN_THREAD_BY_IDENTIFIER,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        params = DialogScreen.Params(
          title = DialogScreen.Text.Id(R.string.catalog_toolbar_enter_identifier),
          description = DialogScreen.Text.Id(R.string.catalog_toolbar_enter_identifier_description),
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
                snackbarManager.toast(screenKey = CatalogScreen.SCREEN_KEY, message = "Failed to parse \'$value'\"")
                return@PositiveDialogButton
              }

              when (resolvedDescriptor) {
                is ResolvedDescriptor.CatalogOrThread -> {
                  when (resolvedDescriptor.chanDescriptor) {
                    is CatalogDescriptor -> {
                      catalogScreenViewModel.loadCatalog(resolvedDescriptor.chanDescriptor)
                    }
                    is ThreadDescriptor -> {
                      threadScreenViewModel.loadThread(resolvedDescriptor.chanDescriptor)

                      globalUiInfoManager.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)
                    }
                  }
                }
                is ResolvedDescriptor.Post -> {
                  val loadOptions = PostScreenViewModel.LoadOptions(
                    scrollToPost = resolvedDescriptor.postDescriptor
                  )

                  threadScreenViewModel.loadThread(
                    threadDescriptor = resolvedDescriptor.postDescriptor.threadDescriptor,
                    loadOptions = loadOptions
                  )

                  globalUiInfoManager.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)
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
    const val ACTION_CATALOG_ALBUM = 5
    const val ACTION_HISTORY_SCREEN_POSITION = 6
  }

}