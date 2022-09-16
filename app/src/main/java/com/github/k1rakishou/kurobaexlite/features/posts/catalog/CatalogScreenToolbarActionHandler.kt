package com.github.k1rakishou.kurobaexlite.features.posts.catalog

import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.AddToHistoryAllCatalogThreads
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.BookmarkAllCatalogThreads
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class CatalogScreenToolbarActionHandler(
  private val componentActivity: ComponentActivity,
  private val screenCoroutineScope: CoroutineScope
) {
  private val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  private val appResources: AppResources by inject(AppResources::class.java)
  private val appSettings: AppSettings by inject(AppSettings::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  private val bookmarkAllCatalogThreads: BookmarkAllCatalogThreads by inject(BookmarkAllCatalogThreads::class.java)
  private val addToHistoryAllCatalogThreads: AddToHistoryAllCatalogThreads by inject(AddToHistoryAllCatalogThreads::class.java)

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
      is ToolbarMenuItems -> {
        when (menuItem.menuItemKey) {
          ToolbarMenuItems.Reload -> {
            catalogScreenViewModel.reload(PostScreenViewModel.LoadOptions(deleteCached = true))
          }
          ToolbarMenuItems.Album -> {
            val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
              ?: return

            val albumScreen = ComposeScreen.createScreen<AlbumScreen>(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              args = { putParcelable(AlbumScreen.CHAN_DESCRIPTOR_ARG, catalogDescriptor) }
            )

            navigationRouter.pushScreen(albumScreen)
          }
          ToolbarMenuItems.OpenThreadByIdentifier -> {
            handleOpenThreadByIdentifier(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter
            )
          }
          ToolbarMenuItems.CatalogDevMenu -> {
            handleDevMenu(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
            )
          }
          ToolbarMenuItems.ScrollTop -> catalogScreenViewModel.scrollTop()
          ToolbarMenuItems.ScrollBottom -> catalogScreenViewModel.scrollBottom()
        }
      }
      is PostViewMode -> {
        screenCoroutineScope.launch {
          val newCatalogPostViewMode = (menuItem.menuItemKey as PostViewMode).toPostViewModeSetting()
          appSettings.catalogPostViewMode.write(newCatalogPostViewMode)
          catalogScreenViewModel.reparseCatalogPostsWithNewViewMode()
        }
      }
      is CatalogGridModeColumnCountOption -> {
        val columnCount = (menuItem.menuItemKey as CatalogGridModeColumnCountOption)
          .count
          .coerceIn(AppSettings.CATALOG_MIN_COLUMN_COUNT, AppSettings.CATALOG_MAX_COLUMN_COUNT)

        screenCoroutineScope.launch {
          appSettings.catalogGridModeColumnCount.write(columnCount)
        }
      }
    }
  }

  private fun handleDevMenu(
    componentActivity: ComponentActivity,
    navigationRouter: NavigationRouter
  ) {
    val floatingMenuItems = mutableListOf<FloatingMenuItem>()

    floatingMenuItems += FloatingMenuItem.Text(
      menuItemKey = DevMenuItems.BookmarkAllCatalogThreads,
      text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_bookmark_all_catalog_threads)
    )

    floatingMenuItems += FloatingMenuItem.Text(
      menuItemKey = DevMenuItems.AddToHistoryAllCatalogThreads,
      text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_add_to_history_all_catalog_threads)
    )

    navigationRouter.presentScreen(
      FloatingMenuScreen(
        floatingMenuKey = FloatingMenuScreen.CATALOG_OVERFLOW_LAYOUT_TYPE,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        menuItems = floatingMenuItems,
        onMenuItemClicked = { clickedMenuItem ->
          when (clickedMenuItem.menuItemKey as DevMenuItems) {
            DevMenuItems.BookmarkAllCatalogThreads -> {
              val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
                ?: return@FloatingMenuScreen

              bookmarkAllCatalogThreads.await(catalogDescriptor)
            }
            DevMenuItems.AddToHistoryAllCatalogThreads -> {
              val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
                ?: return@FloatingMenuScreen

              addToHistoryAllCatalogThreads.await(catalogDescriptor)
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
          inputs = listOf(DialogScreen.Input.String()),
          negativeButton = DialogScreen.DialogButton(buttonText = R.string.cancel),
          positiveButton = DialogScreen.PositiveDialogButton(
            buttonText = R.string.ok,
            onClick = { results ->
              val value = results.firstOrNull()
              if (value == null || value.isEmpty()) {
                return@PositiveDialogButton
              }

              val resolvedDescriptor = homeScreenViewModel.resolveDescriptorFromRawIdentifier(value)
              if (resolvedDescriptor == null) {
                snackbarManager.toast(
                  screenKey = CatalogScreen.SCREEN_KEY,
                  message = appResources.string(R.string.catalog_toolbar_failed_to_parse_input, value)
                )

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

  enum class ToolbarMenuItems {
    OpenThreadByIdentifier,
    Reload,
    Album,
    ScrollTop,
    ScrollBottom,
    CatalogDevMenu,
  }

  enum class DevMenuItems {
    AddToHistoryAllCatalogThreads,
    BookmarkAllCatalogThreads
  }

  @Parcelize
  data class CatalogGridModeColumnCountOption(val count: Int) : Parcelable

}