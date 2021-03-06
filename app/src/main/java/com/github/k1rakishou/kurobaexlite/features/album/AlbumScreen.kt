package com.github.k1rakishou.kurobaexlite.features.album

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostImageThumbnail
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeError
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeErrorWithButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class AlbumScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<AlbumScreen.ToolbarIcon>>(screenArgs, componentActivity, navigationRouter) {
  private val albumScreenViewModel: AlbumScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val mediaViewerPostListScroller: MediaViewerPostListScroller by inject(MediaViewerPostListScroller::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false

  private val chanDescriptor: ChanDescriptor by requireArgumentLazy(CHAN_DESCRIPTOR_ARG)

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
    SimpleToolbarStateBuilder.Builder<ToolbarIcon>(componentActivity)
      .titleId(R.string.album_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = DefaultToolbarIcon.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = DefaultToolbarIcon.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  private val selectionToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcon>(componentActivity)
      .leftIcon(KurobaToolbarIcon(key = SelectionToolbarIcon.Close, drawableId = R.drawable.ic_baseline_close_24))
      .addRightIcon(KurobaToolbarIcon(key = SelectionToolbarIcon.ToggleSelection, drawableId = R.drawable.ic_baseline_select_all_24))
      .addRightIcon(KurobaToolbarIcon(key = SelectionToolbarIcon.Download, drawableId = R.drawable.ic_baseline_download_24))
      .build(selectionToolbarStateKey)
  }

  override val defaultToolbar by lazy {
    SimpleToolbar<ToolbarIcon>(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  private val selectionToolbar by lazy {
    SimpleToolbar<ToolbarIcon>(
      toolbarKey = selectionToolbarKey,
      simpleToolbarState = selectionToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<ToolbarIcon>>(kurobaToolbarContainerViewModelKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val context = LocalContext.current

    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { icon ->
          icon as DefaultToolbarIcon

          when (icon) {
            DefaultToolbarIcon.Back -> { onBackPressed() }
            DefaultToolbarIcon.Overflow -> {
              // no-op
            }
          }
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        selectionToolbarState.iconClickEvents.collect { icon ->
          icon as SelectionToolbarIcon

          when (icon) {
            SelectionToolbarIcon.Close -> { onBackPressed() }
            SelectionToolbarIcon.ToggleSelection -> {
              albumScreenViewModel.toggleSelectionGlobal()
            }
            SelectionToolbarIcon.Download -> {
              albumScreenViewModel.downloadSelectedImages(
                chanDescriptor = chanDescriptor,
                onResult = { activeDownload ->
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

    val toolbarContainerKey = "${screenKey.key}_${keySuffix}"

    KurobaToolbarContainer(
      toolbarContainerKey = toolbarContainerKey,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = { true }
    )
  }

  override val screenContentLoadedFlow: StateFlow<Boolean> by lazy { MutableStateFlow(true) }

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    super.onDisposed(screenDisposeEvent)

    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      albumScreenViewModel.clearAllImageKeys()
      albumScreenViewModel.clearSelection()
    }
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copy(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = windowInsets.top + toolbarHeight
      ).asPaddingValues()
    }

    HandleBackPresses {
      val selectionToolbarOnTop = kurobaToolbarContainerState.topChildToolbarAs<SimpleToolbar<ToolbarIcon>>()
        ?.toolbarKey == selectionToolbarKey

      if (kurobaToolbarContainerState.onBackPressed()) {
        if (selectionToolbarOnTop && albumScreenViewModel.selectedImages.isNotEmpty()) {
          albumScreenViewModel.clearSelection()
        }

        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    val postsAsyncData by when (chanDescriptor) {
      is CatalogDescriptor -> catalogScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
      is ThreadDescriptor -> threadScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
    }

    GradientBackground(
      modifier = Modifier.fillMaxSize()
    ) {
      ContentInternal(
        postsAsyncData = postsAsyncData,
        paddingValues = paddingValues,
        onThumbnailClicked = { postImage ->
          val mediaViewerScreen = ComposeScreen.createScreen<MediaViewerScreen>(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            args = {
              val mediaViewerParams = when (val descriptor = chanDescriptor) {
                is CatalogDescriptor -> MediaViewerParams.Catalog(descriptor, postImage.fullImageAsString)
                is ThreadDescriptor -> MediaViewerParams.Thread(descriptor, postImage.fullImageAsString)
              }

              putParcelable(MediaViewerScreen.mediaViewerParamsKey, mediaViewerParams)
              putParcelable(MediaViewerScreen.openedFromScreenKey, screenKey)
            }
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

    if (album.albumImages.isEmpty()) {
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

    val isInSelectionMode by remember {
      derivedStateOf { albumScreenViewModel.selectedImages.isNotEmpty() }
    }

    val selectedItemsCount by remember {
      derivedStateOf { albumScreenViewModel.selectedImages.size }
    }

    LaunchedEffect(
      key1 = isInSelectionMode,
      block = {
        if (isInSelectionMode) {
          kurobaToolbarContainerState.setToolbar(selectionToolbar)
        } else if (kurobaToolbarContainerState.contains(selectionToolbarKey)) {
          kurobaToolbarContainerState.popToolbar(selectionToolbarKey)
        }
      }
    )

    LaunchedEffect(
      key1 = selectedItemsCount,
      block = {
        selectionToolbarState.toolbarTitleState.value = "Selected ${selectedItemsCount} images"
      }
    )

    LazyVerticalGridWithFastScroller(
      modifier = Modifier.fillMaxSize(),
      columns = GridCells.Fixed(3),
      lazyGridState = lazyGridState,
      contentPadding = paddingValues,
      content = {
        val albumImages = album.albumImages

        albumImages.fastForEachIndexed { index, albumImage ->
          item(
            key = albumImages[index].postImage.fullImageAsString,
            content = {
              val albumImage = albumImages[index]

              AlbumImageItem(
                isInSelectionMode = isInSelectionMode,
                albumImage = albumImage,
                onThumbnailClicked = onThumbnailClicked
              )
            }
          )
        }
      }
    )
  }

  @Composable
  private fun AlbumImageItem(
    isInSelectionMode: Boolean,
    albumImage: AlbumScreenViewModel.AlbumImage,
    onThumbnailClicked: (IPostImage) -> Unit
  ) {
    var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }
    val selected = albumScreenViewModel.isImageSelected(albumImage)

    Box {
      PostImageThumbnail(
        modifier = Modifier
          .fillMaxWidth()
          .height(160.dp)
          .padding(1.dp)
          .onGloballyPositioned { layoutCoordinates ->
            boundsInWindowMut = layoutCoordinates.boundsInWindow()
          },
        showShimmerEffectWhenLoading = true,
        postImage = albumImage.postImage,
        contentScale = ContentScale.Crop,
        onLongClick = { albumScreenViewModel.toggleImageSelection(albumImage) },
        onClickWithError = { clickedImageResult ->
          if (clickedImageResult.isFailure) {
            val error = clickedImageResult.exceptionOrThrow()

            snackbarManager.errorToast(
              message = error.errorMessageOrClassName(),
              screenKey = MainScreen.SCREEN_KEY
            )

            return@PostImageThumbnail
          }

          val clickedPostImage = clickedImageResult.getOrThrow()

          if (isInSelectionMode) {
            albumScreenViewModel.toggleImageSelection(albumImage)
            return@PostImageThumbnail
          }

          val boundsInWindow = boundsInWindowMut
          if (boundsInWindow == null) {
            return@PostImageThumbnail
          }

          clickedThumbnailBoundsStorage.storeBounds(clickedPostImage, boundsInWindow)
          onThumbnailClicked(clickedPostImage)
        }
      )

      if (albumImage.postSubject.isNotNullNorEmpty()) {
        PostSubject(albumImage.postSubject)
      }

      if (isInSelectionMode) {
        SelectionMark(selected)
      }

      ImageInfo(albumImage)
    }
  }

  @Composable
  private fun BoxScope.SelectionMark(selected: Boolean) {
    if (selected) {
      Image(
        modifier = Modifier
          .size(36.dp)
          .align(Alignment.Center),
        painter = painterResource(id = R.drawable.ic_selection_checkmark_with_bg_24dp),
        contentDescription = null
      )
    }

    Image(
      modifier = Modifier
        .size(36.dp)
        .align(Alignment.Center),
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

  interface ToolbarIcon

  enum class DefaultToolbarIcon : ToolbarIcon {
    Back,
    Overflow
  }

  enum class SelectionToolbarIcon : ToolbarIcon{
    Close,
    ToggleSelection,
    Download
  }

  companion object {
    const val CHAN_DESCRIPTOR_ARG = "chan_descriptor"

    val SCREEN_KEY = ScreenKey("AlbumScreen")
  }
}