package com.github.k1rakishou.kurobaexlite.features.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.detectTouches
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.ui.UiNavigationElement
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.LeftIconInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.MiddlePartInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.RightPartInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.detectListScrollEvents
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class HistoryScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {
  private val historyScreenViewModel: HistoryScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val deleteNavElementIconWidth = 40.dp

  override val screenKey: ScreenKey = SCREEN_KEY

  override val hasFab: Boolean = false

  // TODO(KurobaEx): not implemented
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)

  private val historyToolbarState by lazy {
    return@lazy KurobaToolbarState(
      leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_arrow_back_24),
      middlePartInfo = MiddlePartInfo(centerContent = false),
      rightPartInfo = RightPartInfo(
        ToolbarIcon(HistoryToolbarIcons.Overflow, R.drawable.ic_baseline_more_vert_24),
      )
    )
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val screenContentLoaded by screenContentLoadedFlow.collectAsState()
    val historyScreenOnLeftSideMut by appSettings.historyScreenOnLeftSide.listen().collectAsState(initial = null)
    val historyScreenOnLeftSide = historyScreenOnLeftSideMut

    LaunchedEffect(
      key1 = screenContentLoaded,
      block = {
        historyToolbarState.rightPartInfo?.let { rightPartInfo ->
          rightPartInfo.toolbarIcons.forEach { toolbarIcon ->
            if (toolbarIcon.key == HistoryToolbarIcons.Overflow) {
              return@forEach
            }

            toolbarIcon.iconVisible.value = screenContentLoaded
          }
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        // TODO(KurobaEx):
        historyToolbarState.toolbarTitleState.value = "History"
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        historyToolbarState.toolbarIconClickEventFlow.collect { key ->
          when (key as HistoryToolbarIcons) {
            HistoryToolbarIcons.Overflow -> {
              // TODO(KurobaEx):
            }
          }
        }
      }
    )

    KurobaToolbar(
      screenKey = screenKey,
      kurobaToolbarState = historyToolbarState,
      canProcessBackEvent = { true },
      onLeftIconClicked = {
        if (historyScreenOnLeftSide == null) {
          return@KurobaToolbar
        }

        if (historyScreenOnLeftSide) {
          globalUiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY)
        } else {
          globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
        }
      },
      onMiddleMenuClicked = null,
      onSearchQueryUpdated = null
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    val chanTheme = LocalChanTheme.current
    val windowInsets = LocalWindowInsets.current
    val context = LocalContext.current

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val navigationHistoryList = historyScreenViewModel.navigationHistoryList
    val circleCropTransformation = remember { CircleCropTransformation() }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(
      key1 = Unit,
      block = {
        historyScreenViewModel.scrollNavigationHistoryToTopEvents.collectLatest {
          val firstCompletelyVisibleItem = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { lazyListItemInfo -> lazyListItemInfo.offset >= 0 }
            ?: return@collectLatest

          if (firstCompletelyVisibleItem.index <= 1) {
            lazyListState.scrollToItem(0)
          }
        }
      })

    LaunchedEffect(
      key1 = Unit,
      block = {
        historyScreenViewModel.removedElementsFlow.collectLatest { (index, uiNavElement) ->
          val title = when (uiNavElement) {
            is UiNavigationElement.Catalog -> {
              uiNavElement.chanDescriptor.asReadableString()
            }
            is UiNavigationElement.Thread -> {
              uiNavElement.title ?: uiNavElement.chanDescriptor.asReadableString()
            }
          }

          snackbarManager.pushSnackbar(
            SnackbarInfo(
              snackbarId = SnackbarId.NavHistoryElementRemoved,
              aliveUntil = SnackbarInfo.snackbarDuration(
                AppConstants.deleteNavHistoryTimeoutMs.milliseconds
              ),
              content = listOf(
                SnackbarContentItem.Text(
                  context.getString(R.string.navigation_history_screen_removed_navigation_item_text, title)
                ),
                SnackbarContentItem.Spacer(space = 8.dp),
                SnackbarContentItem.Button(
                  key = SnackbarButton.UndoNavHistoryDeletion,
                  text = context.getString(R.string.undo),
                  data = Pair(index, uiNavElement)
                ),
                SnackbarContentItem.Spacer(space = 8.dp),
              )
            )
          )
        }
      })

    LaunchedEffect(
      key1 = Unit,
      block = {
        snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
          if (snackbarClickable.key !is SnackbarButton) {
            return@collectLatest
          }

          when (snackbarClickable.key as SnackbarButton) {
            SnackbarButton.UndoNavHistoryDeletion -> {
              val pair = snackbarClickable.data as? Pair<Int, UiNavigationElement>
                ?: return@collectLatest

              val prevIndex = pair.first
              val uiNavigationElement = pair.second

              historyScreenViewModel.undoNavElementDeletion(prevIndex, uiNavigationElement)
            }
          }
        }
      })

    Box(
      modifier = Modifier
        .background(chanTheme.backColorCompose)
        .consumeClicks(enabled = true)
    ) {
      val contentPadding = remember(key1 = windowInsets) {
        PaddingValues(top = toolbarHeight + windowInsets.top, bottom = windowInsets.bottom)
      }

      LazyColumnWithFastScroller(
        modifier = Modifier
          .fillMaxSize()
          .detectListScrollEvents(
            token = "onHistoryListScrolled",
            onListScrolled = { delta ->
              globalUiInfoManager.onContentListScrolling(screenKey, delta)

              detectTouchingTopOrBottomOfList(
                lazyListState = lazyListState,
                onListTouchingTopOrBottomStateChanged = { touching ->
                  globalUiInfoManager.onContentListTouchingTopOrBottomStateChanged(
                    screenKey = screenKey,
                    touching = touching
                  )
                }
              )
            }
          )
          .pointerInput(
            key1 = Unit,
            block = {
              detectTouches { touching ->
                globalUiInfoManager.onCurrentlyTouchingContentList(screenKey, touching)
              }
            }
          ),
        onFastScrollerDragStateChanged = { dragging ->
          globalUiInfoManager.onFastScrollerDragStateChanged(screenKey, dragging)
        },
        lazyListState = lazyListState,
        contentPadding = contentPadding,
        content = {
          items(
            count = navigationHistoryList.size,
            key = { index -> navigationHistoryList[index].key },
            contentType = { index ->
              return@items when (navigationHistoryList[index]) {
                is UiNavigationElement.Catalog -> ContentType.Catalog
                is UiNavigationElement.Thread -> ContentType.Thread
              }
            },
            itemContent = { index ->
              val navigationElement = navigationHistoryList[index]

              NavigationElement(
                navigationElement = navigationElement,
                circleCropTransformation = circleCropTransformation
              )
            }
          )
        }
      )
    }
  }

  private fun detectTouchingTopOrBottomOfList(
    lazyListState: LazyListState,
    onListTouchingTopOrBottomStateChanged: (Boolean) -> Unit
  ) {
    val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
    val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
    val maxAllowedOffset = 64

    if (firstVisibleItem != null && lastVisibleItem != null) {
      val firstVisibleItemIndex = firstVisibleItem.index
      val firstVisibleItemOffset = firstVisibleItem.offset
      val lastVisibleItemIndex = lastVisibleItem.index

      val totalCount = lazyListState.layoutInfo.totalItemsCount
      val touchingTop = firstVisibleItemIndex <= 0 && firstVisibleItemOffset in 0..maxAllowedOffset
      val touchingBottom = lastVisibleItemIndex >= (totalCount - 1)

      onListTouchingTopOrBottomStateChanged(touchingTop || touchingBottom)
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun LazyItemScope.NavigationElement(
    navigationElement: UiNavigationElement,
    circleCropTransformation: CircleCropTransformation,
  ) {
    Column(
      modifier = Modifier
        .animateItemPlacement()
    ) {
      when (navigationElement) {
        is UiNavigationElement.Catalog -> {
          CatalogNavigationElement(
            navigationElement = navigationElement,
            circleCropTransformation = circleCropTransformation,
            onItemClicked = { element ->
              catalogScreenViewModel.loadCatalog(element.chanDescriptor)
              globalUiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY)
              historyScreenViewModel.reorderNavigationElement(element)
            },
            onRemoveClicked = { element ->
              historyScreenViewModel.removeNavigationElement(element)
            }
          )
        }
        is UiNavigationElement.Thread -> {
          ThreadNavigationElement(
            navigationElement = navigationElement,
            circleCropTransformation = circleCropTransformation,
            onItemClicked = { element ->
              threadScreenViewModel.loadThread(element.chanDescriptor)
              globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
              historyScreenViewModel.reorderNavigationElement(element)
            },
            onRemoveClicked = { element ->
              historyScreenViewModel.removeNavigationElement(element)
            }
          )
        }
      }
    }
  }

  @Composable
  private fun CatalogNavigationElement(
    navigationElement: UiNavigationElement.Catalog,
    circleCropTransformation: CircleCropTransformation,
    onItemClicked: (UiNavigationElement.Catalog) -> Unit,
    onRemoveClicked: (UiNavigationElement.Catalog) -> Unit
  ) {

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(vertical = 2.dp)
        .kurobaClickable(onClick = { onItemClicked(navigationElement) }),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(deleteNavElementIconWidth)
          .padding(2.dp)
          .kurobaClickable(
            bounded = false,
            onClick = { onRemoveClicked(navigationElement) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )

      Spacer(modifier = Modifier.width(4.dp))

      if (navigationElement.iconUrl != null) {
        val thumbnailSize = dimensionResource(id = R.dimen.history_or_bookmark_thumbnail_size)

        NavigationIcon(
          modifier = Modifier.size(thumbnailSize),
          iconUrl = navigationElement.iconUrl,
          circleCropTransformation = circleCropTransformation
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      val title = remember { navigationElement.chanDescriptor.asReadableString() }

      KurobaComposeText(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }

  @Composable
  private fun ThreadNavigationElement(
    navigationElement: UiNavigationElement.Thread,
    circleCropTransformation: CircleCropTransformation,
    onItemClicked: (UiNavigationElement.Thread) -> Unit,
    onRemoveClicked: (UiNavigationElement.Thread) -> Unit
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(vertical = 2.dp)
        .kurobaClickable(onClick = { onItemClicked(navigationElement) }),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(deleteNavElementIconWidth)
          .padding(2.dp)
          .kurobaClickable(
            bounded = false,
            onClick = { onRemoveClicked(navigationElement) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )

      Spacer(modifier = Modifier.width(4.dp))

      if (navigationElement.iconUrl != null) {
        val thumbnailSize = dimensionResource(id = R.dimen.history_or_bookmark_thumbnail_size)

        NavigationIcon(
          modifier = Modifier.size(thumbnailSize),
          iconUrl = navigationElement.iconUrl,
          circleCropTransformation = circleCropTransformation
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      val title = remember {
        if (navigationElement.title.isNullOrEmpty()) {
          navigationElement.chanDescriptor.asReadableString()
        } else {
          navigationElement.title
        }
      }

      KurobaComposeText(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }

  @Composable
  private fun NavigationIcon(
    modifier: Modifier = Modifier,
    iconUrl: String,
    circleCropTransformation: CircleCropTransformation,
  ) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier) {
      val density = LocalDensity.current
      val desiredSizePx = with(density) { remember { 24.dp.roundToPx() } }

      val iconHeightDp = with(density) {
        remember(key1 = constraints.maxHeight) {
          desiredSizePx.coerceAtMost(constraints.maxHeight).toDp()
        }
      }
      val iconWidthDp = with(density) {
        remember(key1 = constraints.maxWidth) {
          desiredSizePx.coerceAtMost(constraints.maxWidth).toDp()
        }
      }

      SubcomposeAsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = ImageRequest.Builder(context)
          .data(iconUrl)
          .crossfade(true)
          .transformations(circleCropTransformation)
          .build(),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        content = {
          val state = painter.state

          if (state is AsyncImagePainter.State.Error) {
            logcatError {
              "NavigationIcon() url=${iconUrl}, error=${state.result.throwable.errorMessageOrClassName()}"
            }

            KurobaComposeIcon(
              modifier = Modifier
                .size(iconWidthDp, iconHeightDp)
                .align(Alignment.Center),
              drawableId = R.drawable.ic_baseline_warning_24
            )

            return@SubcomposeAsyncImage
          }

          SubcomposeAsyncImageContent()
        }
      )
    }
  }

  enum class ContentType {
    Catalog,
    Thread
  }

  enum class SnackbarButton {
    UndoNavHistoryDeletion
  }

  private enum class HistoryToolbarIcons {
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("HistoryScreen")
  }

}