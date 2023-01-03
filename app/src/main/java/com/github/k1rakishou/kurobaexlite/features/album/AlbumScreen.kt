package com.github.k1rakishou.kurobaexlite.features.album

import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostImageLongtapContextMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostImageThumbnail
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.resource.IAppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.koinViewModel
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeError
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.androidx.viewmodel.ext.android.viewModel

class AlbumScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<AlbumScreen.ToolbarIcons>>(screenArgs, componentActivity, navigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false

  private val chanDescriptor: ChanDescriptor by requireArgumentLazy(CHAN_DESCRIPTOR_ARG)

  private val albumScreenViewModel: AlbumScreenViewModel by componentActivity.viewModel<AlbumScreenViewModel>()

  private val postImageLongtapContextMenu by lazy {
    PostImageLongtapContextMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val keySuffix by lazy {
    when (chanDescriptor) {
      is CatalogDescriptor -> "catalog"
      is ThreadDescriptor -> "thread"
    }
  }

  private val kurobaToolbarContainerViewModelKey by lazy { "${screenKey.key}_${keySuffix}" }
  private val defaultToolbarKey by lazy { "${screenKey.key}_${keySuffix}_toolbar" }
  private val defaultToolbarStateKey by lazy { "${defaultToolbarKey}_state" }
  private val selectionToolbarKey by lazy { "${screenKey.key}_${keySuffix}_selection_toolbar" }
  private val selectionToolbarStateKey by lazy { "${selectionToolbarKey}_state" }

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .titleId(R.string.album_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = DefaultToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = DefaultToolbarIcons.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  private val selectionToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .leftIcon(KurobaToolbarIcon(key = SelectionToolbarIcons.Close, drawableId = R.drawable.ic_baseline_close_24))
      .addRightIcon(KurobaToolbarIcon(key = SelectionToolbarIcons.ToggleSelection, drawableId = R.drawable.ic_baseline_select_all_24))
      .addRightIcon(KurobaToolbarIcon(key = SelectionToolbarIcons.Download, drawableId = R.drawable.ic_baseline_download_24))
      .build(selectionToolbarStateKey)
  }

  override val defaultToolbar by lazy {
    SimpleToolbar<ToolbarIcons>(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  private val selectionToolbar by lazy {
    SimpleToolbar<ToolbarIcons>(
      toolbarKey = selectionToolbarKey,
      simpleToolbarState = selectionToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<ToolbarIcons>>(kurobaToolbarContainerViewModelKey)
  }

  override val screenContentLoadedFlow: StateFlow<Boolean> by lazy { MutableStateFlow(true) }

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      componentActivity.koinViewModel<AlbumScreenViewModel>().let { albumScreenViewModel ->
        albumScreenViewModel.clearAllImageKeys()
        albumScreenViewModel.clearSelection()
      }
    }

    super.onDisposed(screenDisposeEvent)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val coroutineScope = rememberCoroutineScope()
    val currentChanDescriptor = getAndListenForChanDescriptor(chanDescriptor)

    with(boxScope) {
      ToolbarInternal(
        defaultToolbarState = defaultToolbarState,
        selectionToolbarState = selectionToolbarState,
        kurobaToolbarContainerState = kurobaToolbarContainerState,
        chanDescriptor = currentChanDescriptor,
        toolbarContainerKey = "${screenKey.key}_${keySuffix}",
        showOverflowMenu = {
          screenCoroutineScope.launch {
            navigationRouter.presentScreen(
              FloatingMenuScreen(
                floatingMenuKey = FloatingMenuScreen.ALBUM_OVERFLOW,
                componentActivity = componentActivity,
                navigationRouter = navigationRouter,
                menuItems = floatingMenuItems(),
                onMenuItemClicked = { menuItem ->
                  coroutineScope.launch {
                    if (menuItem.menuItemKey is AlbumGridModeColumnCountOption) {
                      val albumColumnCount = (menuItem.menuItemKey as AlbumGridModeColumnCountOption)
                        .count
                        .coerceIn(AppSettings.ALBUM_MIN_COLUMN_COUNT, AppSettings.ALBUM_MAX_COLUMN_COUNT)

                      appSettings.albumColumnCount.write(albumColumnCount)
                      return@launch
                    }

                    if (menuItem.menuItemKey is ToolbarMenuItems) {
                      when (menuItem.menuItemKey as ToolbarMenuItems) {
                        ToolbarMenuItems.ShowImageInfo -> appSettings.albumShowImageInfo.toggle()
                      }
                    }
                  }
                }
              )
            )
          }
        },
        onBackPressed = { coroutineScope.launch { onBackPressed() } }
      )
    }
  }
  @Composable
  override fun HomeNavigationScreenContent() {
    val windowInsets = LocalWindowInsets.current
    val view = LocalView.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copyInsets(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = windowInsets.top + toolbarHeight
      ).asPaddingValues()
    }

    HandleBackPresses {
      val selectionToolbarOnTop = kurobaToolbarContainerState.topChildToolbarAs<SimpleToolbar<ToolbarIcons>>()
        ?.toolbarKey == selectionToolbarKey

      if (kurobaToolbarContainerState.onBackPressed()) {
        val albumScreenViewModel = componentActivity.koinViewModel<AlbumScreenViewModel>()

        if (selectionToolbarOnTop && albumScreenViewModel.selectedImages.isNotEmpty()) {
          albumScreenViewModel.clearSelection()
        }

        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    val currentChanDescriptor = getAndListenForChanDescriptor(chanDescriptor)

    GradientBackground(
      modifier = Modifier.fillMaxSize()
    ) {
      KurobaComposeFadeIn {
        ContentInternal(
          paddingValues = paddingValues,
          screenKey = screenKey,
          chanDescriptor = currentChanDescriptor,
          defaultToolbarState = defaultToolbarState,
          selectionToolbarState = selectionToolbarState,
          onThumbnailClicked = { postImage ->
            val mediaViewerScreen = ComposeScreen.createScreen<MediaViewerScreen>(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              args = {
                val mediaViewerParams = when (currentChanDescriptor) {
                  is CatalogDescriptor -> MediaViewerParams.Catalog(currentChanDescriptor, postImage.fullImageAsString)
                  is ThreadDescriptor -> MediaViewerParams.Thread(currentChanDescriptor, postImage.fullImageAsString)
                }

                putParcelable(MediaViewerScreen.mediaViewerParamsKey, mediaViewerParams)
                putParcelable(MediaViewerScreen.openedFromScreenKey, screenKey)
              },
              callbacks = {
                callback(
                  callbackKey = MediaViewerScreen.openingCatalogOrScreenCallbackKey,
                  func = { popScreen() }
                )
              }
            )

            navigationRouter.presentScreen(mediaViewerScreen)
          },
          onThumbnailLongClicked = { albumImage, postImage ->
            if (albumScreenViewModel.isInSelectionMode()) {
              albumScreenViewModel.toggleImageSelection(albumImage)
              return@ContentInternal
            }

            postImageLongtapContextMenu.showMenu(
              postImage = postImage,
              viewProvider = { view },
              onAlbumScreenToggleSelection = { albumScreenViewModel.toggleImageSelection(albumImage) }
            )
          },
          onSelectionModeChanged = { isInSelectionMode ->
            if (isInSelectionMode) {
              kurobaToolbarContainerState.setToolbar(selectionToolbar)
            } else if (kurobaToolbarContainerState.contains(selectionToolbarKey)) {
              kurobaToolbarContainerState.popToolbar(selectionToolbarKey)
            }
          },
          isTopmostScreen = { navigationRouter.isTopmostScreen(this@AlbumScreen) }
        )
      }
    }
  }

  @Composable
  private fun getAndListenForChanDescriptor(chanDescriptor: ChanDescriptor): ChanDescriptor {
    var currentChanDescriptor by remember { mutableStateOf<ChanDescriptor>(chanDescriptor) }

    val threadScreenViewModel = koinRememberViewModel<ThreadScreenViewModel>()
    val catalogScreenViewModel = koinRememberViewModel<CatalogScreenViewModel>()

    LaunchedEffect(
      key1 = Unit,
      block = {
        when (chanDescriptor) {
          is CatalogDescriptor -> {
            catalogScreenViewModel.currentlyOpenedCatalogFlow.collect { catalogDescriptor ->
              if (catalogDescriptor != null) {
                currentChanDescriptor = catalogDescriptor
              }
            }
          }
          is ThreadDescriptor -> {
            threadScreenViewModel.currentlyOpenedThreadFlow.collect { threadDescriptor ->
              if (threadDescriptor != null) {
                currentChanDescriptor = threadDescriptor
              }
            }
          }
        }
      }
    )

    return currentChanDescriptor
  }


  private suspend fun floatingMenuItems(): List<FloatingMenuItem> {
    val menuItems = mutableListOf<FloatingMenuItem>()

    menuItems += kotlin.run {
      return@run FloatingMenuItem.Check(
        menuItemKey = ToolbarMenuItems.ShowImageInfo,
        isChecked = { appSettings.albumShowImageInfo.read() },
        text = FloatingMenuItem.MenuItemText.Id(R.string.album_toolbar_album_show_image_info)
      )
    }

    menuItems += kotlin.run {
      val albumColumnCount = appSettings.albumColumnCount.read()
      val checkedMenuItemKey = AlbumGridModeColumnCountOption(albumColumnCount)

      return@run FloatingMenuItem.NestedItems(
        text = FloatingMenuItem.MenuItemText.Id(R.string.album_toolbar_album_grid_mode_column_count),
        moreItems = listOf(
          FloatingMenuItem.Group(
            checkedMenuItemKey = checkedMenuItemKey,
            groupItems = (AppSettings.ALBUM_MIN_COLUMN_COUNT until AppSettings.ALBUM_MAX_COLUMN_COUNT).map { columnCount ->
              val option = AlbumGridModeColumnCountOption(columnCount)

              val text = if (columnCount == 0) {
                appResources.string(R.string.catalog_toolbar_catalog_grid_mode_column_count_auto)
              } else {
                appResources.string(R.string.catalog_toolbar_catalog_grid_mode_column_count_n, columnCount)
              }

              return@map FloatingMenuItem.Text(
                menuItemKey = option,
                text = FloatingMenuItem.MenuItemText.String(text)
              )
            }
          )
        )
      )
    }

    return menuItems
  }

  @Parcelize
  data class AlbumGridModeColumnCountOption(val count: Int) : Parcelable

  interface ToolbarIcons

  enum class ToolbarMenuItems {
    ShowImageInfo
  }

  enum class DefaultToolbarIcons : ToolbarIcons {
    Back,
    Overflow
  }

  enum class SelectionToolbarIcons : ToolbarIcons{
    Close,
    ToggleSelection,
    Download
  }

  companion object {
    private const val TAG = "AlbumScreen"
    const val CHAN_DESCRIPTOR_ARG = "chan_descriptor"

    val SCREEN_KEY = ScreenKey("AlbumScreen")

    internal const val NEW_ALBUM_IMAGES_ADDED_TOAST_ID = "new_album_images_added_toast_id"
  }
}

@Composable
private fun BoxScope.ToolbarInternal(
  defaultToolbarState: SimpleToolbarState<AlbumScreen.ToolbarIcons>,
  selectionToolbarState: SimpleToolbarState<AlbumScreen.ToolbarIcons>,
  kurobaToolbarContainerState: KurobaToolbarContainerState<SimpleToolbar<AlbumScreen.ToolbarIcons>>,
  chanDescriptor: ChanDescriptor,
  toolbarContainerKey: String,
  showOverflowMenu: () -> Unit,
  onBackPressed: () -> Unit
) {
  val context = LocalContext.current

  val albumScreenViewModel: AlbumScreenViewModel = koinRememberViewModel()
  val snackbarManager: SnackbarManager = koinRemember()

  LaunchedEffect(
    key1 = Unit,
    block = {
      defaultToolbarState.iconClickEvents.collect { icon ->
        icon as AlbumScreen.DefaultToolbarIcons

        when (icon) {
          AlbumScreen.DefaultToolbarIcons.Back -> {
            onBackPressed()
          }
          AlbumScreen.DefaultToolbarIcons.Overflow -> {
            showOverflowMenu()
          }
        }
      }
    }
  )

  LaunchedEffect(
    key1 = chanDescriptor,
    block = {
      selectionToolbarState.iconClickEvents.collect { icon ->
        icon as AlbumScreen.SelectionToolbarIcons

        when (icon) {
          AlbumScreen.SelectionToolbarIcons.Close -> {
            onBackPressed()
          }
          AlbumScreen.SelectionToolbarIcons.ToggleSelection -> {
            albumScreenViewModel.toggleSelectionGlobal()
          }
          AlbumScreen.SelectionToolbarIcons.Download -> {
            albumScreenViewModel.downloadSelectedImages(
              chanDescriptor = chanDescriptor,
              onResult = { activeDownload ->
                if (activeDownload == null) {
                  return@downloadSelectedImages
                }

                val message = context.resources.getString(
                  R.string.media_viewer_download_success_multiple,
                  activeDownload.downloaded,
                  activeDownload.failed,
                  activeDownload.total
                )

                snackbarManager.toast(
                  message = message,
                  screenKey = MainScreen.SCREEN_KEY
                )
              }
            )
            onBackPressed()
          }
        }
      }
    }
  )

  KurobaToolbarContainer(
    toolbarContainerKey = toolbarContainerKey,
    kurobaToolbarContainerState = kurobaToolbarContainerState,
    canProcessBackEvent = { true }
  )
}

@Composable
private fun ContentInternal(
  screenKey: ScreenKey,
  chanDescriptor: ChanDescriptor,
  paddingValues: PaddingValues,
  defaultToolbarState: SimpleToolbarState<AlbumScreen.ToolbarIcons>,
  selectionToolbarState: SimpleToolbarState<AlbumScreen.ToolbarIcons>,
  onThumbnailClicked: (IPostImage) -> Unit,
  onThumbnailLongClicked: (AlbumScreenViewModel.AlbumImage, IPostImage) -> Unit,
  onSelectionModeChanged: (Boolean) -> Unit,
  isTopmostScreen: () -> Boolean,
) {
  val albumScreenViewModel: AlbumScreenViewModel = koinRememberViewModel()
  val mediaViewerPostListScroller: MediaViewerPostListScroller = koinRemember()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val snackbarManager: SnackbarManager = koinRemember()
  val appResources: IAppResources = koinRemember()

  val kurobaSnackbarState = rememberKurobaSnackbarState()

  LaunchedEffect(
    key1 = Unit,
    block = {
      albumScreenViewModel.snackbarFlow.collect { message ->
        if (!isTopmostScreen()) {
          return@collect
        }

        // Hardcode for now, we only have 1 snackbar emitted from the albumScreenViewModel
        snackbarManager.toast(
          message = message,
          screenKey = screenKey,
          toastId = AlbumScreen.NEW_ALBUM_IMAGES_ADDED_TOAST_ID
        )
      }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      albumScreenViewModel.toolbarTitleInfo.collect { toolbarTitleInfo ->
        defaultToolbarState.toolbarTitleState.value = appResources.string(
          R.string.album_screen_toolbar_title_with_images,
          toolbarTitleInfo.albumImagesCount
        )
      }
    }
  )

  val albumMut by albumScreenViewModel.album.collectAsState()
  val album = albumMut

  LaunchedEffect(
    key1 = chanDescriptor,
    block = { albumScreenViewModel.loadAlbumAndListenForUpdates(chanDescriptor) }
  )

  if (album == null) {
    KurobaComposeLoadingIndicator()
    return
  }

  if (album.albumImages.isEmpty()) {
    KurobaComposeError(errorMessage = stringResource(id = R.string.album_screen_album_empty))
    return
  }

  val lazyGridState = rememberLazyGridState()

  LaunchedEffect(
    key1 = Unit,
    block = {
      delay(32)
      lazyGridState.scrollToItem(album.scrollIndex)
    }
  )

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

  val isInSelectionMode by remember {
    derivedStateOf { albumScreenViewModel.selectedImages.isNotEmpty() }
  }

  val selectedItemsCount by remember {
    derivedStateOf { albumScreenViewModel.selectedImages.size }
  }

  LaunchedEffect(
    key1 = isInSelectionMode,
    block = { onSelectionModeChanged(isInSelectionMode) }
  )

  LaunchedEffect(
    key1 = selectedItemsCount,
    block = {
      selectionToolbarState.toolbarTitleState.value = appResources.string(
        R.string.album_screen_selected_n_images,
        selectedItemsCount
      )
    }
  )

  val albumGridModeColumnCount by globalUiInfoManager.albumGridModeColumnCount.collectAsState()
  val albumShowImageInfo by globalUiInfoManager.albumShowImageInfo.collectAsState()

  val columns = remember(key1 = albumGridModeColumnCount) {
    if (albumGridModeColumnCount <= 0) {
      GridCells.Adaptive(minSize = 140.dp)
    } else {
      GridCells.Fixed(count = albumGridModeColumnCount)
    }
  }

  LazyVerticalGridWithFastScroller(
    columns = columns,
    lazyGridState = lazyGridState,
    contentPadding = paddingValues,
    content = {
      val albumImages = album.albumImages

      albumImages.fastForEach { albumImage ->
        item(
          key = albumImage.postImage.uniqueKey(),
          content = {
            AlbumImageItem(
              isInSelectionMode = isInSelectionMode,
              albumShowImageInfo = albumShowImageInfo,
              albumImage = albumImage,
              onThumbnailClicked = onThumbnailClicked,
              onThumbnailLongClicked = onThumbnailLongClicked,
            )
          }
        )
      }
    }
  )

  KurobaSnackbarContainer(
    modifier = Modifier.fillMaxSize(),
    screenKey = screenKey,
    isTablet = globalUiInfoManager.isTablet,
    kurobaSnackbarState = kurobaSnackbarState
  )
}

@Composable
private fun AlbumImageItem(
  isInSelectionMode: Boolean,
  albumShowImageInfo: Boolean,
  albumImage: AlbumScreenViewModel.AlbumImage,
  onThumbnailClicked: (IPostImage) -> Unit,
  onThumbnailLongClicked: (AlbumScreenViewModel.AlbumImage, IPostImage) -> Unit
) {
  val albumScreenViewModel: AlbumScreenViewModel = koinRememberViewModel()
  val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage = koinRemember()

  var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }
  val selected = albumScreenViewModel.isImageSelected(albumImage)
  // TODO: uncomment once LazyStaggeredGrid doesn't crash anymore upon scroll position correction
//  val ratio = remember(key1 = albumImage.postImage) { aspectRatioFromImageDimensions(albumImage.postImage) }

  val imageItemScaleAnimation by animateFloatAsState(targetValue = if (selected) 0.85f else 1f)

  val onClickRemembered = remember(
    isInSelectionMode,
    albumImage,
    albumScreenViewModel,
    clickedThumbnailBoundsStorage,
    boundsInWindowMut
  ) {
    onClick@ { clickedPostImage: IPostImage ->
      if (isInSelectionMode) {
        albumScreenViewModel.toggleImageSelection(albumImage)
        return@onClick
      }

      val boundsInWindow = boundsInWindowMut
      if (boundsInWindow == null) {
        return@onClick
      }

      clickedThumbnailBoundsStorage.storeBounds(clickedPostImage, boundsInWindow)
      onThumbnailClicked(clickedPostImage)
    }
  }

  val onLongClickRemembered = remember(albumImage) {
    { postImage: IPostImage -> onThumbnailLongClicked(albumImage, postImage) }
  }

  Box {
    PostImageThumbnail(
      modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
//        .aspectRatio(ratio)
        .padding(1.dp)
        .graphicsLayer {
          scaleX = imageItemScaleAnimation
          scaleY = imageItemScaleAnimation
        }
        .onGloballyPositioned { layoutCoordinates ->
          boundsInWindowMut = layoutCoordinates.boundsInWindow()
        },
      showShimmerEffectWhenLoading = true,
      postImage = albumImage.postImage,
      contentScale = ContentScale.Crop,
      onLongClick = onLongClickRemembered,
      onClick = onClickRemembered,
    )

    if (albumShowImageInfo && albumImage.postSubject.isNotNullNorEmpty()) {
      PostSubject(albumImage.postSubject)
    }

    if (isInSelectionMode) {
      SelectionMark(selected)
    }

    if (albumShowImageInfo) {
      ImageInfo(albumImage)
    }
  }
}

@Composable
private fun BoxScope.SelectionMark(selected: Boolean) {
  val density = LocalDensity.current
  val topLeftOffset = with(density) { remember { IntOffset(8.dp.roundToPx(), 8.dp.roundToPx()) } }

  val checkmarkScaleAnimation by animateFloatAsState(targetValue = if (selected) 1f else 0f)

  Image(
    modifier = Modifier
      .size(36.dp)
      .offset { topLeftOffset }
      .graphicsLayer {
        scaleX = checkmarkScaleAnimation
        scaleY = checkmarkScaleAnimation
      }
      .align(Alignment.TopStart),
    painter = painterResource(id = R.drawable.ic_selection_checkmark_with_bg_24dp),
    contentDescription = null
  )

  Image(
    modifier = Modifier
      .size(36.dp)
      .offset { topLeftOffset }
      .align(Alignment.TopStart),
    painter = painterResource(id = R.drawable.ic_selection_circle_24dp),
    contentDescription = null
  )
}

@Composable
private fun BoxScope.PostSubject(subject: String) {
  val bgColor = remember { Color.Black.copy(alpha = 0.5f) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .align(Alignment.TopCenter)
      .background(bgColor)
      .padding(2.dp)
  ) {
    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = subject,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = Color.White,
      textAlign = TextAlign.Center,
      fontSize = 12.sp
    )
  }
}

@Composable
private fun BoxScope.ImageInfo(albumImage: AlbumScreenViewModel.AlbumImage) {
  val bgColor = remember { Color.Black.copy(alpha = 0.5f) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .align(Alignment.BottomCenter)
      .background(bgColor)
      .padding(2.dp)
  ) {
    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = albumImage.imageOriginalFileName,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = Color.White,
      textAlign = TextAlign.Center,
      fontSize = 12.sp
    )

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = albumImage.imageInfo,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = Color.White,
      textAlign = TextAlign.Center,
      fontSize = 11.sp
    )
  }
}

private const val MAX_RATIO = 2f
private const val MIN_RATIO = .4f

private fun aspectRatioFromImageDimensions(postImage: IPostImage?): Float {
  val imageWidth = postImage?.width ?: 0
  val imageHeight = postImage?.height ?: 0
  if (imageWidth <= 0 || imageHeight <= 0) {
    return 1f
  }

  var ratio = imageWidth.toFloat() / imageHeight.toFloat()
  if (ratio > MAX_RATIO) {
    ratio = MAX_RATIO
  }

  if (ratio < MIN_RATIO) {
    ratio = MIN_RATIO
  }

  return ratio
}