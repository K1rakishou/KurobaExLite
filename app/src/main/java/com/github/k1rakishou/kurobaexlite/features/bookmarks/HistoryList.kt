package com.github.k1rakishou.kurobaexlite.features.bookmarks

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.UiNavigationElement
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "HistoryList"
private val deleteNavElementIconWidth = 40.dp
private val circleCropTransformation = CircleCropTransformation()

private const val noHistoryAddedMessageItemKey = "no_history_added_message"
private const val noHistoryFoundMessageItemKey = "no_history_found_message"
private const val searchInputItemKey = "search_input"

@Composable
fun HistoryList() {
  val windowInsets = LocalWindowInsets.current
  val context = LocalContext.current

  val historyScreenViewModel: HistoryScreenViewModel = koinRememberViewModel()
  val snackbarManager: SnackbarManager = koinRemember()

  val navigationHistoryListBeforeFiltering = historyScreenViewModel.navigationHistoryList
  val lazyListState = rememberLazyListState()

  val contentPadding = remember(key1 = windowInsets) {
    PaddingValues(bottom = windowInsets.bottom)
  }

  var searchQuery by rememberSaveable(
    key = "history_search_query",
    stateSaver = TextFieldValue.Saver
  ) { mutableStateOf<TextFieldValue>(TextFieldValue()) }

  var navigationHistoryList by remember { mutableStateOf(navigationHistoryListBeforeFiltering) }
  var isInSearchMode by remember { mutableStateOf(false) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      combine(
        flow = snapshotFlow { navigationHistoryListBeforeFiltering },
        flow2 = snapshotFlow { searchQuery },
        transform = { a, b -> a to b }
      ).collectLatest { (navigationHistory, query) ->
        if (query.text.isEmpty()) {
          Snapshot.withMutableSnapshot {
            navigationHistoryList = navigationHistory
            isInSearchMode = false
          }

          return@collectLatest
        }

        delay(250L)

        val historyListAfterFiltering = navigationHistory
          .filter { uiNavigationElement -> uiNavigationElement.matchesQuery(query.text) }

        Snapshot.withMutableSnapshot {
          navigationHistoryList = historyListAfterFiltering
          isInSearchMode = true
        }
      }
    })

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
                context.getString(
                  R.string.navigation_history_screen_removed_navigation_item_text,
                  title
                )
              ),
              SnackbarContentItem.Spacer(space = 8.dp),
              SnackbarContentItem.Button(
                key = BookmarksScreen.HistorySnackbarButton.UndoNavHistoryDeletion,
                text = context.getString(R.string.undo),
                data = Pair(index, uiNavElement)
              ),
              SnackbarContentItem.Spacer(space = 8.dp),
            )
          )
        )
      }
    })

  LazyColumnWithFastScroller(
    lazyListContainerModifier = Modifier.fillMaxSize(),
    lazyListState = lazyListState,
    contentPadding = contentPadding,
    content = {
      if (navigationHistoryList.isEmpty() && !isInSearchMode) {
        item(
          key = noHistoryAddedMessageItemKey,
          contentType = noHistoryAddedMessageItemKey,
          content = {
            KurobaComposeText(
              modifier = Modifier
                .fillParentMaxSize()
                .padding(8.dp),
              text = stringResource(id = R.string.history_screen_no_history_added),
              textAlign = TextAlign.Center,
              fontSize = 16.sp
            )
          }
        )
      } else {
        item(
          key = searchInputItemKey,
          contentType = searchInputItemKey,
          content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              DrawerSearchInput(
                modifier = Modifier
                  .weight(1f)
                  .wrapContentHeight(),
                searchQuery = searchQuery,
                searchingBookmarks = false,
                onSearchQueryChanged = { query -> searchQuery = query },
                onClearSearchQueryClicked = { searchQuery = TextFieldValue() }
              )

              Spacer(modifier = Modifier.width(8.dp))

              DrawerContentTypeToggleIcon()

              Spacer(modifier = Modifier.width(8.dp))
            }
          }
        )

        if (navigationHistoryList.isEmpty() && isInSearchMode) {
          item(
            key = noHistoryFoundMessageItemKey,
            contentType = noHistoryFoundMessageItemKey,
            content = {
              KurobaComposeText(
                modifier = Modifier
                  .fillParentMaxSize()
                  .padding(8.dp),
                text = stringResource(id = R.string.history_screen_nothing_found_by_query, searchQuery.text),
                textAlign = TextAlign.Center,
                fontSize = 16.sp
              )
            }
          )
        } else {
          items(
            count = navigationHistoryList.size,
            key = { index -> navigationHistoryList[index].key },
            contentType = { "history_item" },
            itemContent = { index ->
              val navigationElement = navigationHistoryList[index]

              NavigationElement(
                searchQuery = searchQuery.text,
                navigationElement = navigationElement
              )
            }
          )
        }
      }
    }
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.NavigationElement(
  searchQuery: String,
  navigationElement: UiNavigationElement,
) {
  val catalogScreenViewModel: CatalogScreenViewModel = koinRememberViewModel()
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val historyScreenViewModel: HistoryScreenViewModel = koinRememberViewModel()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  Column(
    modifier = Modifier
      .animateItemPlacement()
  ) {
    when (navigationElement) {
      is UiNavigationElement.Catalog -> {
        CatalogNavigationElement(
          searchQuery = searchQuery,
          navigationElement = navigationElement,
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
          searchQuery = searchQuery,
          navigationElement = navigationElement,
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
  searchQuery: String,
  navigationElement: UiNavigationElement.Catalog,
  onItemClicked: (UiNavigationElement.Catalog) -> Unit,
  onRemoveClicked: (UiNavigationElement.Catalog) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val postCommentApplier = koinRemember<PostCommentApplier>()

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
      )

      Spacer(modifier = Modifier.width(8.dp))
    }

    val textFormatted = remember(key1 = searchQuery, key2 = chanTheme) {
      val title = navigationElement.chanDescriptor.asReadableString()

      return@remember buildAnnotatedString {
        val titleFormatted = buildAnnotatedString { withStyle(SpanStyle(color = chanTheme.textColorPrimary)) { append(title) } }

        if (searchQuery.isNotEmpty()) {
          val (_, titleFormattedWithSearchQuery) = postCommentApplier.markOrUnmarkSearchQuery(
            chanTheme = chanTheme,
            searchQuery = searchQuery,
            minQueryLength = 2,
            string = titleFormatted
          )

          append(titleFormattedWithSearchQuery)
        } else {
          append(titleFormatted)
        }
      }
    }

    KurobaComposeText(
      modifier = Modifier.fillMaxWidth(),
      text = textFormatted,
      fontSize = 15.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun ThreadNavigationElement(
  searchQuery: String,
  navigationElement: UiNavigationElement.Thread,
  onItemClicked: (UiNavigationElement.Thread) -> Unit,
  onRemoveClicked: (UiNavigationElement.Thread) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val postCommentApplier = koinRemember<PostCommentApplier>()

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
        iconUrl = navigationElement.iconUrl
      )

      Spacer(modifier = Modifier.width(8.dp))
    }

    val textFormatted = remember(key1 = searchQuery, key2 = chanTheme) {
      val title = if (navigationElement.title.isNullOrEmpty()) {
        navigationElement.chanDescriptor.asReadableString()
      } else {
        navigationElement.title
      }

      return@remember buildAnnotatedString {
        val titleFormatted = buildAnnotatedString { withStyle(SpanStyle(color = chanTheme.textColorPrimary)) { append(title) } }

        if (searchQuery.isNotEmpty()) {
          val (_, titleFormattedWithSearchQuery) = postCommentApplier.markOrUnmarkSearchQuery(
            chanTheme = chanTheme,
            searchQuery = searchQuery,
            minQueryLength = 2,
            string = titleFormatted
          )

          append(titleFormattedWithSearchQuery)
        } else {
          append(titleFormatted)
        }
      }
    }

    KurobaComposeText(
      modifier = Modifier.fillMaxWidth(),
      text = textFormatted,
      fontSize = 15.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun NavigationIcon(
  modifier: Modifier = Modifier,
  iconUrl: String,
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
        .size(Size.ORIGINAL)
        .build(),
      contentScale = ContentScale.Crop,
      contentDescription = null,
      content = {
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
          logcatError(TAG) {
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