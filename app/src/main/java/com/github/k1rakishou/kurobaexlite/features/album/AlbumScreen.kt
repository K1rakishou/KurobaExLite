package com.github.k1rakishou.kurobaexlite.features.album

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.ImageThumbnail
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeError
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeErrorWithButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class AlbumScreen(
  private val chanDescriptor: ChanDescriptor,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {
  private val albumScreenViewModel: AlbumScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val mediaViewerPostListScroller: MediaViewerPostListScroller by inject(MediaViewerPostListScroller::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false

  private val defaultToolbarKey = "${screenKey.key}_toolbar"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcon>(componentActivity)
      .titleId(R.string.album_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcon.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcon.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  private val defaultToolbar by lazy {
    SimpleToolbar(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    KurobaToolbarContainerState<SimpleToolbar<ToolbarIcon>>(screenKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { icon ->
          when (icon) {
            ToolbarIcon.Back -> { onBackPressed() }
            ToolbarIcon.Overflow -> {
              // no-op
            }
          }
        }
      }
    )

    val keySuffix = when (chanDescriptor) {
      is CatalogDescriptor -> "catalog"
      is ThreadDescriptor -> "thread"
    }

    KurobaToolbarContainer(
      toolbarContainerKey = "${screenKey.key}_${keySuffix}",
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = { true }
    )
  }

  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)

  @Composable
  override fun HomeNavigationScreenContent() {
    val chanTheme = LocalChanTheme.current
    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copy(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = windowInsets.top + toolbarHeight
      ).asPaddingValues()
    }

    LaunchedEffect(
      key1 = Unit,
      block = { kurobaToolbarContainerState.setToolbar(defaultToolbar) }
    )

    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    val postsAsyncData by when (chanDescriptor) {
      is CatalogDescriptor -> catalogScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
      is ThreadDescriptor -> threadScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose)
    ) {
      ContentInternal(
        postsAsyncData = postsAsyncData,
        paddingValues = paddingValues,
        onThumbnailClicked = { postImage ->
          val mediaViewerParams = when (chanDescriptor) {
            is CatalogDescriptor -> MediaViewerParams.Catalog(chanDescriptor, postImage.fullImageAsUrl)
            is ThreadDescriptor -> MediaViewerParams.Thread(chanDescriptor, postImage.fullImageAsUrl)
          }

          val mediaViewerScreen = MediaViewerScreen(
            mediaViewerParams = mediaViewerParams,
            openedFromScreen = screenKey,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter
          )

          navigationRouter.presentScreen(mediaViewerScreen)
        }
      )
    }
  }

  @Composable
  private fun ContentInternal(
    postsAsyncData: AsyncData<PostsState>,
    paddingValues: PaddingValues,
    onThumbnailClicked: (IPostImage) -> Unit
  ) {
    if (postsAsyncData is AsyncData.Uninitialized) {
      return
    }

    val coroutineScope = rememberCoroutineScope()
    val postCellDataList = (postsAsyncData as? AsyncData.Data)?.data?.posts
    var albumMut by remember { mutableStateOf<AlbumScreenViewModel.Album?>(null) }
    val album = albumMut

    if (postsAsyncData is AsyncData.Error) {
      val errorMessage = remember { postsAsyncData.error.errorMessageOrClassName() }

      KurobaComposeErrorWithButton(
        errorMessage = errorMessage,
        buttonText = stringResource(id = R.string.reload),
        onButtonClicked = {
          coroutineScope.launch {
            val freshPostCellDataList = (postsAsyncData as? AsyncData.Data)?.data?.posts
              ?: return@launch

            albumScreenViewModel.loadAlbumFromPostStateList(chanDescriptor, freshPostCellDataList)
          }
        }
      )

      return
    }

    LaunchedEffect(
      key1 = postCellDataList,
      block = {
        if (postCellDataList == null) {
          albumMut = null
          return@LaunchedEffect
        }

        albumMut = albumScreenViewModel.loadAlbumFromPostStateList(chanDescriptor, postCellDataList)
      }
    )

    if (album == null || postsAsyncData is AsyncData.Loading) {
      KurobaComposeLoadingIndicator()
      return
    }

    if (album.images.isEmpty()) {
      KurobaComposeError(errorMessage = stringResource(id = R.string.album_screen_album_empty))
      return
    }

    val lazyGridState = rememberLazyGridState(initialFirstVisibleItemIndex = album.scrollIndex)

    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        mediaViewerPostListScroller.scrollEventFlow.collect { scrollInfo ->
          if (screenKey != scrollInfo.screenKey) {
            return@collect
          }

          val indexToScroll = album.imageIndexByPostDescriptor(scrollInfo.postDescriptor)
          if (indexToScroll != null) {
            lazyGridState.scrollToItem(index = indexToScroll)
          }
        }
      }
    )

    LazyVerticalGridWithFastScroller(
      modifier = Modifier.fillMaxSize(),
      columns = GridCells.Fixed(3),
      lazyGridState = lazyGridState,
      contentPadding = paddingValues,
      content = {
        val albumItems = album.images

        albumItems.fastForEachIndexed { index, albumItem ->
          item(
            key = albumItems[index].fullImageAsString,
            content = {
              val postImage = albumItems[index]
              var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }

              ImageThumbnail(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(160.dp)
                  .padding(1.dp)
                  .onGloballyPositioned { layoutCoordinates ->
                    boundsInWindowMut = layoutCoordinates.boundsInWindow()
                  }
                  .kurobaClickable(
                    onClick = {
                      val boundsInWindow = boundsInWindowMut
                      if (boundsInWindow == null) {
                        return@kurobaClickable
                      }

                      clickedThumbnailBoundsStorage.storeBounds(postImage, boundsInWindow)
                      onThumbnailClicked(postImage)
                    }
                  ),
                showShimmerEffectWhenLoading = true,
                postImage = postImage,
                contentScale = ContentScale.Crop
              )
            }
          )
        }
      }
    )
  }

  enum class ToolbarIcon {
    Back,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("AlbumScreen")
  }
}