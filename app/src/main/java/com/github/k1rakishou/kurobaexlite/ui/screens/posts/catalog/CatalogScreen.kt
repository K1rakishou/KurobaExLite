package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.PostsScreenToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel

class CatalogScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean
) : PostsScreen(componentActivity, navigationRouter, isStartScreen) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  private val toolbarMenuItems: List<ToolbarMenuItem> by lazy {
    listOf(
      ToolbarMenuItem.TextMenu(
        menuItemId = ACTION_RELOAD,
        textId = R.string.reload
      ),
      ToolbarMenuItem.TextMenu(
        menuItemId = ACTION_OPEN_THREAD_BY_IDENTIFIER,
        textId = R.string.catalog_toolbar_open_thread_by_identifier,
        subTextId = R.string.catalog_toolbar_open_thread_by_identifier_subtitle
      )
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = true

  @Composable
  override fun postDataAsync(): AsyncData<List<State<PostData>>> {
    return catalogScreenViewModel.postScreenState.postDataAsyncState()
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    with(boxScope) {
      PostsScreenToolbar(
        isCatalogScreen = isCatalogScreen,
        postListAsync = postDataAsync(),
        onToolbarOverflowMenuClicked = {
          navigationRouter.presentScreen(
            FloatingMenuScreen(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              menuItems = toolbarMenuItems,
              onMenuItemClicked = { menuItem -> processClickedToolbarMenuItem(menuItem) }
            )
          )
        }
      )
    }
  }

  @Composable
  override fun Content() {
    // TODO(KurobaEx): remove this LaunchedEffect at some point

    LaunchedEffect(
      key1 = Unit,
      block = { catalogScreenViewModel.loadCatalog(CatalogDescriptor(Chan4.SITE_KEY, "vg")) }
    )

    RouterHost(
      navigationRouter = navigationRouter,
      defaultScreen = { CatalogPostListScreenContent() }
    )
  }

  @Composable
  private fun CatalogPostListScreenContent() {
    Box(modifier = Modifier.fillMaxSize()) {
      CatalogPostListScreen()

      val parsingPosts by catalogScreenViewModel.parsingPostsAsync
      if (parsingPosts) {
        CatalogOrThreadLoadingIndicator()
      }
    }
  }

  @Composable
  private fun CatalogPostListScreen() {
    PostListContent(
      isCatalogMode = isCatalogScreen,
      mainUiLayoutMode = uiInfoManager.mainUiLayoutMode(),
      postsScreenViewModel = catalogScreenViewModel,
      onPostCellClicked = { postData ->
        // TODO(KurobaEx): come up with a better solution than doing it manually
        homeScreenViewModel.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)

        val threadDescriptor = ThreadDescriptor(
          catalogDescriptor = postData.postDescriptor.catalogDescriptor,
          threadNo = postData.postNo
        )

        threadScreenViewModel.loadThread(threadDescriptor)
      }
    )
  }

  private fun processClickedToolbarMenuItem(menuItem: ToolbarMenuItem) {
    logcat { "catalog processClickedToolbarMenuItem id=${menuItem.menuItemId}" }

    when (menuItem.menuItemId) {
      ACTION_RELOAD -> catalogScreenViewModel.reload()
      ACTION_OPEN_THREAD_BY_IDENTIFIER -> {
        navigationRouter.presentScreen(
          DialogScreen(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            params = DialogScreen.Params(
              title = R.string.catalog_screen_enter_identifier,
              description = R.string.catalog_screen_enter_identifier_description,
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
                    homeScreenViewModel.toast("Failed to parse \'$value'\"")
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
                      // TODO(KurobaEx): do something with the postNo
                    }
                  }
                }
              )
            )
          )
        )
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("CatalogScreen")

    private const val ACTION_OPEN_THREAD_BY_IDENTIFIER = 0
    private const val ACTION_RELOAD = 1
  }

}