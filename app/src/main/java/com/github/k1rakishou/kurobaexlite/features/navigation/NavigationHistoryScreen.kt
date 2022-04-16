package com.github.k1rakishou.kurobaexlite.features.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.ui.UiNavigationElement
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class NavigationHistoryScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val navigationHistoryScreenViewModel: NavigationHistoryScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val windowInsets = LocalWindowInsets.current
    val context = LocalContext.current
    val navigationHistoryList = navigationHistoryScreenViewModel.navigationHistoryList
    val circleCropTransformation = remember { CircleCropTransformation() }
    val navElementHeight = 36.dp
    val lazyListState = rememberLazyListState()

    LaunchedEffect(
      key1 = Unit,
      block = {
        navigationHistoryScreenViewModel.scrollNavigationHistoryToTopEvents.collectLatest {
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
        navigationHistoryScreenViewModel.removedElementsFlow.collectLatest { (index, uiNavElement) ->
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
              aliveUntil = SnackbarInfo.snackbarDuration(5.seconds),
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

              navigationHistoryScreenViewModel.undoNavElementDeletion(prevIndex, uiNavigationElement)
            }
          }
        }
      })

    Box(
      modifier = Modifier
        .background(chanTheme.backColorCompose)
        .consumeClicks(enabled = true)
    ) {
      val contentPadding = remember(key1 = windowInsets) { windowInsets.asPaddingValues() }

      LazyColumnWithFastScroller(
        modifier = Modifier.fillMaxSize(),
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
                index = index,
                lastIndex = navigationHistoryList.lastIndex,
                navElementHeight = navElementHeight,
                navigationElement = navigationElement,
                circleCropTransformation = circleCropTransformation
              )
            }
          )
        }
      )
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun LazyItemScope.NavigationElement(
    index: Int,
    lastIndex: Int,
    navElementHeight: Dp,
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
            navElementHeight = navElementHeight,
            navigationElement = navigationElement,
            circleCropTransformation = circleCropTransformation,
            onItemClicked = { element ->
              catalogScreenViewModel.loadCatalog(element.chanDescriptor)
              uiInfoManager.closeDrawer(withAnimation = true)
              uiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY)
              navigationHistoryScreenViewModel.reorderNavigationElement(element)
            },
            onRemoveClicked = { element ->
              navigationHistoryScreenViewModel.removeNavigationElement(element)
            }
          )
        }
        is UiNavigationElement.Thread -> {
          ThreadNavigationElement(
            navElementHeight = navElementHeight,
            navigationElement = navigationElement,
            circleCropTransformation = circleCropTransformation,
            onItemClicked = { element ->
              threadScreenViewModel.loadThread(element.chanDescriptor)
              uiInfoManager.closeDrawer(withAnimation = true)
              uiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
              navigationHistoryScreenViewModel.reorderNavigationElement(element)
            },
            onRemoveClicked = { element ->
              navigationHistoryScreenViewModel.removeNavigationElement(element)
            }
          )
        }
      }

      if (index < lastIndex) {
        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }

  @Composable
  private fun CatalogNavigationElement(
    navElementHeight: Dp,
    navigationElement: UiNavigationElement.Catalog,
    circleCropTransformation: CircleCropTransformation,
    onItemClicked: (UiNavigationElement.Catalog) -> Unit,
    onRemoveClicked: (UiNavigationElement.Catalog) -> Unit
  ) {

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(navElementHeight)
        .kurobaClickable(onClick = { onItemClicked(navigationElement) }),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .kurobaClickable(
            bounded = false,
            onClick = { onRemoveClicked(navigationElement) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )

      Spacer(modifier = Modifier.width(4.dp))

      if (navigationElement.iconUrl != null) {
        NavigationIcon(
          modifier = Modifier.size(navElementHeight),
          iconUrl = navigationElement.iconUrl,
          circleCropTransformation = circleCropTransformation
        )

        Spacer(modifier = Modifier.width(4.dp))
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
    navElementHeight: Dp,
    navigationElement: UiNavigationElement.Thread,
    circleCropTransformation: CircleCropTransformation,
    onItemClicked: (UiNavigationElement.Thread) -> Unit,
    onRemoveClicked: (UiNavigationElement.Thread) -> Unit
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(navElementHeight)
        .kurobaClickable(onClick = { onItemClicked(navigationElement) }),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .kurobaClickable(
            bounded = false,
            onClick = { onRemoveClicked(navigationElement) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )

      Spacer(modifier = Modifier.width(4.dp))

      if (navigationElement.iconUrl != null) {
        NavigationIcon(
          modifier = Modifier.size(navElementHeight),
          iconUrl = navigationElement.iconUrl,
          circleCropTransformation = circleCropTransformation
        )

        Spacer(modifier = Modifier.width(4.dp))
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

  companion object {
    val SCREEN_KEY = ScreenKey("NavigationHistoryScreen")
  }

}