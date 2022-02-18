package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbar
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.PostsScreenToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class CatalogScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean
) : PostsScreen(componentActivity, navigationRouter, isStartScreen) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)

  private val catalogScreenToolbarActionHandler by lazy {
    CatalogScreenToolbarActionHandler(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      catalogScreenViewModel = catalogScreenViewModel,
      threadScreenViewModel = threadScreenViewModel,
      homeScreenViewModel = homeScreenViewModel
    )
  }

  private val floatingMenuItems: List<FloatingMenuItem> by lazy {
    listOf(
      FloatingMenuItem.Text(
        menuItemId = CatalogScreenToolbarActionHandler.ACTION_RELOAD,
        textId = R.string.reload
      ),
      FloatingMenuItem.Text(
        menuItemId = CatalogScreenToolbarActionHandler.ACTION_OPEN_THREAD_BY_IDENTIFIER,
        textId = R.string.catalog_toolbar_open_thread_by_identifier,
        subTextId = R.string.catalog_toolbar_open_thread_by_identifier_subtitle
      ),
      FloatingMenuItem.Footer(
        items = listOf(
          FloatingMenuItem.Icon(
            menuItemId = CatalogScreenToolbarActionHandler.ACTION_SCROLL_TOP,
            iconId = R.drawable.ic_baseline_arrow_upward_24
          ),
          FloatingMenuItem.Icon(
            menuItemId = CatalogScreenToolbarActionHandler.ACTION_SCROLL_BOTTOM,
            iconId = R.drawable.ic_baseline_arrow_downward_24
          )
        )
      )
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = true

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val postListAsync by catalogScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()

    with(boxScope) {
      PostsScreenToolbar(
        isCatalogScreen = isCatalogScreen,
        postListAsync = postListAsync,
        parsedPostDataCache = parsedPostDataCache,
        navigationRouter = navigationRouter,
        onSearchQueryUpdated = { searchQuery -> catalogScreenViewModel.updateSearchQuery(searchQuery) },
        onToolbarOverflowMenuClicked = {
          navigationRouter.presentScreen(
            FloatingMenuScreen(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              menuItems = floatingMenuItems,
              onMenuItemClicked = { menuItem ->
                catalogScreenToolbarActionHandler.processClickedToolbarMenuItem(menuItem)
              }
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
      block = { catalogScreenViewModel.loadCatalog(CatalogDescriptor(Chan4.SITE_KEY, "g")) }
    )

    RouterHost(
      navigationRouter = navigationRouter,
      defaultScreen = { CatalogPostListScreenContent() }
    )
  }

  @Composable
  private fun CatalogPostListScreenContent() {
    val kurobaSnackbarState = rememberKurobaSnackbarState()

    Box(modifier = Modifier.fillMaxSize()) {
      CatalogPostListScreen()

      KurobaSnackbar(
        modifier = Modifier.fillMaxSize(),
        kurobaSnackbarState = kurobaSnackbarState,
        snackbarEventFlow = catalogScreenViewModel.snackbarEventFlow
      )
    }
  }

  @Composable
  private fun CatalogPostListScreen() {
    val configuration = LocalConfiguration.current

    PostListContent(
      isCatalogMode = isCatalogScreen,
      mainUiLayoutMode = uiInfoManager.mainUiLayoutMode(configuration),
      postsScreenViewModel = catalogScreenViewModel,
      onPostCellClicked = { postData ->
        // TODO(KurobaEx): come up with a better solution than doing it manually
        homeScreenViewModel.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)

        val threadDescriptor = ThreadDescriptor(
          catalogDescriptor = postData.postDescriptor.catalogDescriptor,
          threadNo = postData.postNo
        )

        threadScreenViewModel.loadThread(threadDescriptor)
      },
      onPostListScrolled = { delta ->
        homeScreenViewModel.onChildContentScrolling(delta)
      },
      onPostListTouchingTopOrBottomStateChanged = { touching ->
        homeScreenViewModel.onPostListTouchingTopOrBottomStateChanged(touching)
      },
      onPostListDragStateChanged = { dragging ->
        homeScreenViewModel.onPostListDragStateChanged(dragging)
      },
      onFastScrollerDragStateChanged = { dragging ->
        homeScreenViewModel.onFastScrollerDragStateChanged(dragging)
      },
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("CatalogScreen")
  }

}