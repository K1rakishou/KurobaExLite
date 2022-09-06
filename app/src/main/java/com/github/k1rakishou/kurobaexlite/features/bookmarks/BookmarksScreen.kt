package com.github.k1rakishou.kurobaexlite.features.bookmarks

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
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
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen.BookmarkAnnotatedContent.Companion.bookmarkItemKey
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen.BookmarkAnnotatedContent.Companion.circleCropTransformation
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen.BookmarkAnnotatedContent.Companion.deleteBookmarkIconWidth
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen.BookmarkAnnotatedContent.Companion.grayscaleTransformation
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen.BookmarkAnnotatedContent.Companion.noBookmarksAddedMessageItemKey
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen.BookmarkAnnotatedContent.Companion.noBookmarksFoundMessageItemKey
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen.BookmarkAnnotatedContent.Companion.searchInputItemKey
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.settings.application.AppSettingsScreen
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.image.GrayscaleTransformation
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkStatsUi
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefreshState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.LayoutOrientation
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SlotLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.ReorderableState
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.detectReorder
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.draggedItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.rememberReorderState
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.reorderable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookmarksScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val bookmarksScreenViewModel: BookmarksScreenViewModel by componentActivity.viewModel()

  private val floatingMenuItems: List<FloatingMenuItem> by lazy {
    listOf(
      FloatingMenuItem.Text(
        menuItemKey = ToolbarMenuItems.PruneInactiveBookmarks,
        text = FloatingMenuItem.MenuItemText.Id(R.string.bookmark_screen_toolbar_prune_inactive),
        subText = FloatingMenuItem.MenuItemText.Id(R.string.bookmark_screen_toolbar_prune_inactive_description)
      ),
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val context = LocalContext.current

    ContentInternal(
      openAppSettings = { openAppSettings() },
      showBookmarkOptions = { showBookmarkOptions(context) },
      showRevertBookmarkDeletion = { threadBookmark, oldPosition ->
        showRevertBookmarkDeletion(
          context = context,
          deletedBookmark = threadBookmark,
          oldPosition = oldPosition
        )
      }
    )
  }

  private fun showRevertBookmarkDeletion(
    context: Context,
    deletedBookmark: ThreadBookmark,
    oldPosition: Int
  ) {
    val title = deletedBookmark.title ?: deletedBookmark.threadDescriptor.asReadableString()

    snackbarManager.pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.ThreadBookmarkRemoved,
        aliveUntil = SnackbarInfo.snackbarDuration(AppConstants.deleteBookmarkTimeoutMs.milliseconds),
        content = listOf(
          SnackbarContentItem.Text(
            context.getString(R.string.thread_bookmarks_screen_removed_bookmark_item_text, title)
          ),
          SnackbarContentItem.Spacer(space = 8.dp),
          SnackbarContentItem.Button(
            key = SnackbarButton.UndoThreadBookmarkDeletion,
            text = context.getString(R.string.undo),
            data = Pair(oldPosition, deletedBookmark)
          ),
          SnackbarContentItem.Spacer(space = 8.dp),
        )
      )
    )
  }

  private fun showBookmarkOptions(context: Context) {
    navigationRouter.presentScreen(
      FloatingMenuScreen(
        floatingMenuKey = FloatingMenuScreen.BOOKMARKS_OVERFLOW,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        menuItems = floatingMenuItems,
        onMenuItemClicked = { menuItem ->
          when (menuItem.menuItemKey as ToolbarMenuItems) {
            ToolbarMenuItems.PruneInactiveBookmarks -> {
              onPruneInactiveBookmarksItemClicked(context)
            }
          }
        }
      )
    )
  }

  private fun onPruneInactiveBookmarksItemClicked(
    context: Context
  ) {
    navigationRouter.presentScreen(
      DialogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        params = DialogScreen.Params(
          title = DialogScreen.Text.Id(R.string.bookmark_screen_prune_inactive_bookmarks_dialog),
          negativeButton = DialogScreen.DialogButton(
            buttonText = R.string.bookmark_screen_prune_inactive_bookmarks_dialog_negative_button
          ),
          positiveButton = DialogScreen.PositiveDialogButton(
            buttonText = R.string.bookmark_screen_prune_inactive_bookmarks_dialog_positive_button,
            isActionDangerous = true,
            onClick = {
              bookmarksScreenViewModel.pruneInactiveBookmarks(
                onFinished = { deleteResult ->
                  if (deleteResult.isFailure) {
                    val errorMessage = deleteResult.exceptionOrThrow()
                      .errorMessageOrClassName(userReadable = true)

                    snackbarManager.errorToast(
                      message = context.resources.getString(
                        R.string.bookmark_screen_failed_to_delete_inactive,
                        errorMessage
                      ),
                      screenKey = MainScreen.SCREEN_KEY
                    )
                  } else {
                    val deletedCount = deleteResult.getOrThrow()

                    if (deletedCount > 0) {
                      snackbarManager.toast(
                        message = context.resources.getString(
                          R.string.bookmark_screen_deleted_n_inactive,
                          deletedCount
                        ),
                        screenKey = MainScreen.SCREEN_KEY
                      )
                    } else {
                      snackbarManager.toast(
                        message = context.resources.getString(R.string.bookmark_screen_no_inactive_to_delete),
                        screenKey = MainScreen.SCREEN_KEY
                      )
                    }
                  }
                }
              )
            }
          )
        ),
        canDismissByClickingOutside = false
      )
    )
  }

  private fun openAppSettings() {
    val currentPagerPageScreenKey = globalUiInfoManager.currentPage()?.screenKey
      ?: return

    val currentChildRouter = navigationRouter.getChildRouterByKeyOrNull(currentPagerPageScreenKey)
      ?: return

    globalUiInfoManager.closeDrawer()

    val appSettingsScreen = ComposeScreen.createScreen<AppSettingsScreen>(
      componentActivity = componentActivity,
      navigationRouter = currentChildRouter
    )

    currentChildRouter.pushScreen(appSettingsScreen)
  }

  data class ThreadBookmarkStatsCombined(
    val newPostsAnimated: Int,
    val newQuotesAnimated: Int,
    val totalPostsAnimated: Int,
    val isFirstFetch: Boolean,
    val totalPages: Int,
    val currentPage: Int,
    val isBumpLimit: Boolean,
    val isImageLimit: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val isError: Boolean,
    val isDead: Boolean
  )

  enum class ToolbarMenuItems {
    PruneInactiveBookmarks
  }

  enum class SnackbarButton {
    UndoThreadBookmarkDeletion
  }

  enum class BookmarkAnnotatedContent(val id: String) {
    ThreadDeleted("id_thread_deleted"),
    ThreadArchived("id_thread_archived"),
    ThreadError("id_thread_error");

    companion object {
      internal val circleCropTransformation = CircleCropTransformation()
      internal val grayscaleTransformation = GrayscaleTransformation()

      internal val deleteBookmarkIconWidth = 40.dp
      internal val searchInputItemKey = "search_input"
      internal val noBookmarksAddedMessageItemKey = "no_bookmarks_added_message"
      internal val noBookmarksFoundMessageItemKey = "no_bookmarks_found_message"
      internal val bookmarkItemKey = "thread_bookmark"

      @Composable
      fun Content(bookmarkAnnotatedContent: BookmarkAnnotatedContent, isDead: Boolean) {
        val iconAlpha = remember(key1 = isDead) { if (isDead) 0.5f else 1f }

        val drawableId = remember(key1 = bookmarkAnnotatedContent) {
          when (bookmarkAnnotatedContent) {
            ThreadDeleted -> R.drawable.trash_icon
            ThreadArchived -> R.drawable.archived_icon
            ThreadError -> R.drawable.error_icon
          }
        }

        Image(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = iconAlpha },
          painter = painterResource(id = drawableId),
          contentDescription = null
        )
      }
    }
  }

  companion object {
    internal const val TAG = "BookmarksScreen"
    val SCREEN_KEY = ScreenKey("BookmarksScreen")
  }
}

@Composable
private fun ContentInternal(
  openAppSettings: () -> Unit,
  showBookmarkOptions: () -> Unit,
  showRevertBookmarkDeletion: (ThreadBookmark, Int) -> Unit
) {
  val bookmarksScreenViewModel: BookmarksScreenViewModel = koinRememberViewModel()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val snackbarManager: SnackbarManager = koinRemember()

  val windowInsets = LocalWindowInsets.current
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  var isFullyClosed by remember { mutableStateOf(true) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      globalUiInfoManager.drawerVisibilityFlow.collectLatest { drawerVisibility ->
        val newIsFullyClosed = when (drawerVisibility) {
          DrawerVisibility.Closed -> true
          DrawerVisibility.Closing,
          is DrawerVisibility.Fling,
          is DrawerVisibility.Drag,
          DrawerVisibility.Opened,
          DrawerVisibility.Opening -> false
        }

        if (isFullyClosed != newIsFullyClosed) {
          isFullyClosed = newIsFullyClosed
        }
      }
    })

  if (isFullyClosed) {
    return
  }

  val lazyListState = rememberLazyListState()
  val reorderableState = rememberReorderState(lazyListState = lazyListState)
  val pullToRefreshState = rememberPullToRefreshState()

  LaunchedEffect(
    key1 = Unit,
    block = {
      snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
        if (snackbarClickable.key !is BookmarksScreen.SnackbarButton) {
          return@collectLatest
        }

        when (snackbarClickable.key as BookmarksScreen.SnackbarButton) {
          BookmarksScreen.SnackbarButton.UndoThreadBookmarkDeletion -> {
            val pair = snackbarClickable.data as? Pair<Int, ThreadBookmark>
              ?: return@collectLatest

            val prevPosition = pair.first
            val threadBookmark = pair.second

            bookmarksScreenViewModel.undoBookmarkDeletion(threadBookmark, prevPosition)
          }
        }
      }
    })

  DisposableEffect(
    key1 = Unit,
    effect = {
      onDispose { bookmarksScreenViewModel.clearMarkedBookmarks() }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      bookmarksScreenViewModel.backgroundWatcherEventsFlow.collect {
        pullToRefreshState.stopRefreshing()
      }
    }
  )

  GradientBackground {
    KurobaComposeFadeIn {
      SlotLayout(
        modifier = Modifier.fillMaxSize(),
        layoutOrientation = LayoutOrientation.Vertical,
        builder = {
          fixed(
            size = toolbarHeight + windowInsets.top,
            key = "DrawerHeader",
            content = {
              ThreadBookmarkHeader(
                onShowAppSettingsClicked = { openAppSettings() },
                onShowBookmarkOptionsClicked = { showBookmarkOptions() },
              )
            })

          dynamic(
            weight = 1f,
            key = "BookmarksList",
            content = {
              BookmarksList(
                pullToRefreshState = pullToRefreshState,
                reorderableState = reorderableState,
                showRevertBookmarkDeletion = showRevertBookmarkDeletion
              )
            })
        })
    }
  }
}

@Composable
private fun BookmarksList(
  pullToRefreshState: PullToRefreshState,
  reorderableState: ReorderableState,
  showRevertBookmarkDeletion: (ThreadBookmark, Int) -> Unit
) {
  val context = LocalContext.current
  val windowInsets = LocalWindowInsets.current

  val bookmarksScreenViewModel: BookmarksScreenViewModel = koinRememberViewModel()
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val contentPadding = remember(key1 = windowInsets) { PaddingValues(bottom = windowInsets.bottom) }
  val pullToRefreshToPadding = remember(key1 = contentPadding) { contentPadding.calculateTopPadding() }

  val bookmarkListBeforeFiltering = bookmarksScreenViewModel.bookmarksList
  val canUseFancyAnimations by bookmarksScreenViewModel.canUseFancyAnimations

  var searchQuery by rememberSaveable(
    key = "bookmarks_search_query",
    stateSaver = TextFieldValue.Saver
  ) { mutableStateOf<TextFieldValue>(TextFieldValue()) }

  var bookmarkList by remember { mutableStateOf(bookmarkListBeforeFiltering) }
  var isInSearchMode by remember { mutableStateOf(false) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      combine(
        flow = snapshotFlow { bookmarkListBeforeFiltering },
        flow2 = snapshotFlow { searchQuery },
        transform = { a, b -> a to b }
      ).collectLatest { (bookmarks, query) ->
        if (query.text.isEmpty()) {
          Snapshot.withMutableSnapshot {
            bookmarkList = bookmarks
            isInSearchMode = false
          }

          return@collectLatest
        }

        delay(250L)

        val bookmarkListAfterFiltering = bookmarks
          .filter { threadBookmarkUi -> threadBookmarkUi.matchesQuery(query.text) }

        Snapshot.withMutableSnapshot {
          bookmarkList = bookmarkListAfterFiltering
          isInSearchMode = true
        }
      }
    })

  PullToRefresh(
    pullToRefreshState = pullToRefreshState,
    topPadding = pullToRefreshToPadding,
    onTriggered = { bookmarksScreenViewModel.forceRefreshBookmarks(context) }
  ) {
    LazyColumnWithFastScroller(
      lazyListContainerModifier = Modifier
        .fillMaxSize(),
      lazyListModifier = Modifier
        .fillMaxSize()
        .reorderable(
          state = reorderableState,
          canDragOver = { key, _ -> (key as? String)?.startsWith(bookmarkItemKey) == true },
          // "idx - 1" because we have the SearchInput as the first item of the list
          onMove = { from, to -> bookmarksScreenViewModel.onMove(from - 1, to - 1) },
          onDragEnd = { from, to -> bookmarksScreenViewModel.onMoveConfirmed(from - 1, to - 1) }
        ),
      lazyListState = reorderableState.lazyListState,
      contentPadding = contentPadding,
      content = {
        if (bookmarkList.isEmpty() && !isInSearchMode) {
          item(
            key = noBookmarksAddedMessageItemKey,
            contentType = noBookmarksAddedMessageItemKey,
            content = {
              KurobaComposeText(
                modifier = Modifier
                  .fillParentMaxSize()
                  .padding(8.dp),
                text = stringResource(id = R.string.bookmark_screen_bookmarks_added),
                textAlign = TextAlign.Center,
                fontSize = 16.sp
              )
            }
          )
        } else {
          item(
            key = searchInputItemKey,
            contentType = "search_input_item",
            content = {
              SearchInput(
                searchQuery = searchQuery,
                onSearchQueryChanged = { query -> searchQuery = query },
                onClearSearchQueryClicked = { searchQuery = TextFieldValue() }
              )
            }
          )

          if (bookmarkList.isEmpty() && isInSearchMode) {
            item(
              key = noBookmarksFoundMessageItemKey,
              contentType = noBookmarksFoundMessageItemKey,
              content = {
                KurobaComposeText(
                  modifier = Modifier
                    .fillParentMaxSize()
                    .padding(8.dp),
                  text = stringResource(id = R.string.bookmark_screen_found_by_query, searchQuery.text),
                  textAlign = TextAlign.Center,
                  fontSize = 16.sp
                )
              }
            )
          } else {
            items(
              count = bookmarkList.size,
              key = { index -> dragKey(bookmarkList[index].threadDescriptor) },
              contentType = { "thread_bookmark_item" },
              itemContent = { index ->
                val threadBookmarkUi = bookmarkList[index]

                ThreadBookmarkItem(
                  searchQuery = searchQuery.text,
                  canUseFancyAnimations = canUseFancyAnimations,
                  threadBookmarkUi = threadBookmarkUi,
                  reorderableState = reorderableState,
                  onBookmarkClicked = { clickedThreadBookmarkUi ->
                    threadScreenViewModel.loadThread(clickedThreadBookmarkUi.threadDescriptor)
                    globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
                    globalUiInfoManager.closeDrawer(withAnimation = true)
                  },
                  onBookmarkDeleted = { clickedThreadBookmarkUi ->
                    bookmarksScreenViewModel.deleteBookmark(
                      threadDescriptor = clickedThreadBookmarkUi.threadDescriptor,
                      onBookmarkDeleted = { deletedBookmark, oldPosition ->
                        showRevertBookmarkDeletion(deletedBookmark, oldPosition)
                      }
                    )
                  },
                )
              }
            )
          }
        }
      }
    )
  }
}

@Composable
private fun SearchInput(
  searchQuery: TextFieldValue,
  onSearchQueryChanged: (TextFieldValue) -> Unit,
  onClearSearchQueryClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val bgColor = remember(key1 = chanTheme.backColor) {
    return@remember if (ThemeEngine.isDarkColor(chanTheme.backColor)) {
      ThemeEngine.manipulateColor(chanTheme.backColor, 1.3f)
    } else {
      ThemeEngine.manipulateColor(chanTheme.backColor, 0.7f)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 4.dp, vertical = 4.dp)
      .background(color = bgColor, shape = RoundedCornerShape(corner = CornerSize(size = 4.dp))),
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    Row {
      val density = LocalDensity.current
      var textFieldHeight by remember { mutableStateOf(0.dp) }

      if (searchQuery.text.isNotEmpty() && textFieldHeight > 0.dp) {
        Spacer(modifier = Modifier.width(4.dp))

        KurobaComposeClickableIcon(
          modifier = Modifier.size(textFieldHeight),
          drawableId = R.drawable.ic_baseline_clear_24,
          onClick = { onClearSearchQueryClicked() }
        )

        Spacer(modifier = Modifier.width(4.dp))
      }

      KurobaComposeCustomTextField(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .onGloballyPositioned { layoutCoordinates ->
            with(density) { textFieldHeight = layoutCoordinates.size.height.toDp() }
          },
        value = searchQuery,
        parentBackgroundColor = chanTheme.backColor,
        keyboardOptions = KeyboardOptions(autoCorrect = false),
        drawBottomIndicator = false,
        singleLine = true,
        maxLines = 1,
        fontSize = 18.sp,
        textFieldPadding = remember { PaddingValues(vertical = 4.dp, horizontal = 4.dp) },
        labelText = stringResource(id = R.string.type_to_search_hint),
        onValueChange = { newValue -> onSearchQueryChanged(newValue) }
      )
    }

    Spacer(modifier = Modifier.height(4.dp))
  }
}

@Composable
private fun ThreadBookmarkHeader(
  onShowAppSettingsClicked: () -> Unit,
  onShowBookmarkOptionsClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val windowInsets = LocalWindowInsets.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(chanTheme.backColor)
      .consumeClicks(enabled = true)
  ) {
    Spacer(modifier = Modifier.height(windowInsets.top))

    Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(28.dp)
          .kurobaClickable(bounded = false, onClick = { onShowAppSettingsClicked() }),
        drawableId = R.drawable.ic_baseline_settings_24
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeIcon(
        modifier = Modifier
          .size(28.dp)
          .kurobaClickable(bounded = false, onClick = { onShowBookmarkOptionsClicked() }),
        drawableId = R.drawable.ic_baseline_more_vert_24
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.ThreadBookmarkItem(
  searchQuery: String,
  canUseFancyAnimations: Boolean,
  threadBookmarkUi: ThreadBookmarkUi,
  reorderableState: ReorderableState,
  onBookmarkClicked: (ThreadBookmarkUi) -> Unit,
  onBookmarkDeleted: (ThreadBookmarkUi) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val itemHeight = dimensionResource(id = R.dimen.history_or_bookmark_item_height)
  val animationDurationMs = 500

  val bookmarksScreenViewModel: BookmarksScreenViewModel = koinRememberViewModel()
  val postCommentApplier: PostCommentApplier = koinRemember()

  val isDrawerCurrentlyOpened by listenForDrawerVisibilityEvents()

  val textAnimationSpec = remember(key1 = isDrawerCurrentlyOpened) {
    if (isDrawerCurrentlyOpened && canUseFancyAnimations) {
      tween<Int>(durationMillis = animationDurationMs)
    } else {
      snap<Int>()
    }
  }

  val defaultBgColor = if (bookmarksScreenViewModel.bookmarksToMark.containsKey(threadBookmarkUi.threadDescriptor)) {
    chanTheme.accentColor.copy(alpha = 0.3f)
  } else {
    chanTheme.backColor
  }

  val selectedOnBackColor = remember(key1 = chanTheme.selectedOnBackColor) {
    chanTheme.selectedOnBackColor.copy(alpha = 0.5f)
  }

  var threadBookmarkHash by remember { mutableStateOf(threadBookmarkUi.hashCode()) }
  val bgAnimatable = remember { Animatable(defaultBgColor) }

  LaunchedEffect(
    key1 = threadBookmarkHash,
    key2 = threadBookmarkUi.hashCode(),
    block = {
      if (threadBookmarkUi.hashCode() == threadBookmarkHash) {
        return@LaunchedEffect
      }

      try {
        bgAnimatable.animateTo(selectedOnBackColor, tween(250))
        delay(500)
        bgAnimatable.animateTo(defaultBgColor, tween(250))
      } finally {
        bgAnimatable.snapTo(defaultBgColor)
        threadBookmarkHash = threadBookmarkUi.hashCode()
      }
    }
  )

  val bookmarkBgColor by bgAnimatable.asState()
  val offset by remember(key1 = threadBookmarkUi.threadDescriptor) {
    derivedStateOf {
      reorderableState.offsetByKey(dragKey(threadBookmarkUi.threadDescriptor))
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp)
      .height(itemHeight)
      .draggedItem(offset)
      .kurobaClickable(onClick = { onBookmarkClicked(threadBookmarkUi) })
      .drawBehind {
        if (reorderableState.draggedKey == dragKey(threadBookmarkUi.threadDescriptor)) {
          drawRect(bookmarkBgColor)
        }
      }
      .animateItemPlacement(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (searchQuery.isEmpty()) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(deleteBookmarkIconWidth)
          .padding(2.dp)
          .kurobaClickable(
            bounded = false,
            onClick = { onBookmarkDeleted(threadBookmarkUi) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    val threadBookmarkStatsUi = threadBookmarkUi.threadBookmarkStatsUi
    val isArchived by threadBookmarkStatsUi.isArchived
    val isDeleted by threadBookmarkStatsUi.isDeleted
    val isDead = isArchived || isDeleted
    val thumbnailSize = dimensionResource(id = R.dimen.history_or_bookmark_thumbnail_size)

    val titleMut by threadBookmarkUi.title
    val title = titleMut
    val thumbnailUrlMut by threadBookmarkUi.thumbnailUrl
    val thumbnailUrl = thumbnailUrlMut

    if (thumbnailUrl != null) {
      BookmarkThumbnail(
        modifier = Modifier
          .size(thumbnailSize)
          .graphicsLayer { alpha = if (isDead) 0.5f else 1f },
        iconUrl = thumbnailUrl,
        isDead = isDead
      )

      Spacer(modifier = Modifier.width(8.dp))
    } else {
      Shimmer(
        modifier = Modifier
          .size(thumbnailSize)
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    Column(
      modifier = Modifier.weight(1f)
    ) {
      if (title != null) {
        val textFormatted = remember(key1 = searchQuery, key2 = chanTheme, key3 = isDead) {
          val textColor = if (isDead) {
            chanTheme.textColorHint
          } else {
            chanTheme.textColorPrimary
          }

          return@remember buildAnnotatedString {
            val titleFormatted = buildAnnotatedString { withStyle(SpanStyle(color = textColor)) { append(title) } }

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
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.5f),
          text = textFormatted,
          fontSize = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      } else {
        Shimmer(
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.5f)
        )
      }

      ThreadBookmarkAdditionalInfo(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.5f),
        threadDescriptor = threadBookmarkUi.threadDescriptor,
        threadBookmarkStatsUi = threadBookmarkStatsUi,
        textAnimationSpecProvider = { textAnimationSpec },
      )
    }

    if (searchQuery.isEmpty()) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .padding(all = 4.dp)
          .detectReorder(reorderableState),
        drawableId = R.drawable.ic_baseline_reorder_24
      )
    }

    Spacer(modifier = Modifier.width(4.dp))
  }
}

@Composable
private fun listenForDrawerVisibilityEvents(): State<Boolean> {
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val drawerVisibility by globalUiInfoManager.drawerVisibilityFlow
    .collectAsState(initial = DrawerVisibility.Closed)

  return remember { derivedStateOf { drawerVisibility.isOpened } }
}

@Composable
private fun ThreadBookmarkAdditionalInfo(
  modifier: Modifier,
  threadDescriptor: ThreadDescriptor,
  threadBookmarkStatsUi: ThreadBookmarkStatsUi,
  textAnimationSpecProvider: () -> FiniteAnimationSpec<Int>
) {
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current
  val fontSize = 13.sp

  val transition = updateTransition(
    targetState = threadBookmarkStatsUi,
    label = "Bookmark animation"
  )

  val newPostsAnimated by transition.animateInt(
    label = "New posts text animation",
    transitionSpec = { textAnimationSpecProvider() },
    targetValueByState = { state -> state.newPosts.value }
  )
  val newQuotesAnimated by transition.animateInt(
    label = "New quotes text animation",
    transitionSpec = { textAnimationSpecProvider() },
    targetValueByState = { state -> state.newQuotes.value }
  )
  val totalPostsAnimated by transition.animateInt(
    label = "Total posts text textAnimationSpec",
    transitionSpec = { textAnimationSpecProvider() },
    targetValueByState = { state -> state.totalPosts.value }
  )

  val isFirstFetch by threadBookmarkStatsUi.isFirstFetch
  val totalPages by threadBookmarkStatsUi.totalPages
  val currentPage by threadBookmarkStatsUi.currentPage
  val isBumpLimit by threadBookmarkStatsUi.isBumpLimit
  val isImageLimit by threadBookmarkStatsUi.isImageLimit
  val isArchived by threadBookmarkStatsUi.isArchived
  val isDeleted by threadBookmarkStatsUi.isDeleted
  val isError by threadBookmarkStatsUi.isError
  val isDead = isArchived || isDeleted

  val threadBookmarkStatsCombined by remember(
    newPostsAnimated,
    newQuotesAnimated,
    totalPostsAnimated,
    isFirstFetch,
    totalPages,
    currentPage,
    isBumpLimit,
    isImageLimit,
    isArchived,
    isDeleted,
    isError,
    isDead,
  ) {
    derivedStateOf {
      BookmarksScreen.ThreadBookmarkStatsCombined(
        newPostsAnimated = newPostsAnimated,
        newQuotesAnimated = newQuotesAnimated,
        totalPostsAnimated = totalPostsAnimated,
        isFirstFetch = isFirstFetch,
        totalPages = totalPages,
        currentPage = currentPage,
        isBumpLimit = isBumpLimit,
        isImageLimit = isImageLimit,
        isArchived = isArchived,
        isDeleted = isDeleted,
        isError = isError,
        isDead = isDead
      )
    }
  }

  val bookmarkAdditionalInfoText = remember(threadBookmarkStatsCombined) {
    convertBookmarkStateToText(
      context = context,
      chanTheme = chanTheme,
      threadDescriptor = threadDescriptor,
      threadBookmarkStatsCombined = threadBookmarkStatsCombined
    )
  }

  val bookmarkInlinedContent = remember(key1 = isDead) {
    val resultMap = mutableMapOf<String, InlineTextContent>()

    BookmarksScreen.BookmarkAnnotatedContent.values().forEach { bookmarkAnnotatedContent ->
      resultMap[bookmarkAnnotatedContent.id] = InlineTextContent(
        placeholder = Placeholder(fontSize, fontSize, PlaceholderVerticalAlign.Center),
        children = { BookmarksScreen.BookmarkAnnotatedContent.Content(bookmarkAnnotatedContent, isDead) }
      )
    }

    return@remember resultMap
  }

  KurobaComposeText(
    modifier = modifier,
    color = Color.Unspecified,
    fontSize = fontSize,
    text = bookmarkAdditionalInfoText,
    inlineContent = bookmarkInlinedContent
  )
}

@Composable
private fun BookmarkThumbnail(
  modifier: Modifier = Modifier,
  iconUrl: String,
  isDead: Boolean
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

    val transformations = remember(key1 = isDead) {
      if (isDead) {
        listOf(circleCropTransformation, grayscaleTransformation)
      } else {
        listOf(circleCropTransformation)
      }
    }

    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(context)
        .data(iconUrl)
        .crossfade(true)
        .transformations(transformations)
        .size(Size.ORIGINAL)
        .build(),
      contentScale = ContentScale.Crop,
      contentDescription = null,
      content = {
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
          logcatError(BookmarksScreen.TAG) {
            "BookmarkThumbnail() url=${iconUrl}, error=${state.result.throwable.errorMessageOrClassName()}"
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

private fun convertBookmarkStateToText(
  context: Context,
  chanTheme: ChanTheme,
  threadDescriptor: ThreadDescriptor,
  threadBookmarkStatsCombined: BookmarksScreen.ThreadBookmarkStatsCombined
): AnnotatedString {
  val newPostsAnimated = threadBookmarkStatsCombined.newPostsAnimated
  val totalPostsAnimated = threadBookmarkStatsCombined.totalPostsAnimated
  val newQuotesAnimated = threadBookmarkStatsCombined.newQuotesAnimated
  val totalPages = threadBookmarkStatsCombined.totalPages
  val currentPage = threadBookmarkStatsCombined.currentPage
  val isBumpLimit = threadBookmarkStatsCombined.isBumpLimit
  val isImageLimit = threadBookmarkStatsCombined.isImageLimit
  val isDeleted = threadBookmarkStatsCombined.isDeleted
  val isArchived = threadBookmarkStatsCombined.isArchived
  val isError = threadBookmarkStatsCombined.isError
  val isDead = threadBookmarkStatsCombined.isDead
  val isFirstFetch = threadBookmarkStatsCombined.isFirstFetch

  val defaultTextColor = if (isDead) {
    chanTheme.textColorHint
  } else {
    chanTheme.textColorSecondary
  }

  return buildAnnotatedString {
    pushStyle(SpanStyle(color = defaultTextColor))

    append("/")
    append(threadDescriptor.catalogDescriptor.boardCode)
    append("/")
    append(AppConstants.TEXT_SEPARATOR)

    if (isFirstFetch) {
      append(context.getString(R.string.bookmark_loading_state))
      return@buildAnnotatedString
    }

    append(
      buildAnnotatedString {
        append(
          buildAnnotatedString {
            if (!isDead && newPostsAnimated > 0) {
              pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
            } else {
              pushStyle(SpanStyle(color = defaultTextColor))
            }

            append(newPostsAnimated.toString())
            append("/")
            append(totalPostsAnimated.toString())
          }
        )

        if (newQuotesAnimated > 0) {
          append(" (")
          append(
            buildAnnotatedString {
              if (!isDead && newQuotesAnimated > 0) {
                pushStyle(SpanStyle(color = chanTheme.bookmarkCounterHasRepliesColor))
              } else {
                pushStyle(SpanStyle(color = defaultTextColor))
              }

              append(newQuotesAnimated.toString())
            }
          )
          append(")")
        }
      }
    )

    if (totalPages > 0) {
      if (length > 0) {
        append(AppConstants.TEXT_SEPARATOR)
      }

      append("Pg: ")
      append(
        buildAnnotatedString {
          if (!isDead && currentPage >= totalPages) {
            pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
          } else {
            pushStyle(SpanStyle(color = defaultTextColor))
          }

          append(currentPage.toString())
          append("/")
          append(totalPages.toString())
        }
      )
    }

    if (isBumpLimit) {
      if (length > 0) {
        append(AppConstants.TEXT_SEPARATOR)
      }

      append(
        buildAnnotatedString {
          if (!isDead) {
            pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
          }

          append("BL")
        }
      )
    }

    if (isImageLimit) {
      if (length > 0) {
        append(AppConstants.TEXT_SEPARATOR)
      }

      append(
        buildAnnotatedString {
          if (!isDead) {
            pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
          }

          append("IL")
        }
      )
    }

    if (isDeleted) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(BookmarksScreen.BookmarkAnnotatedContent.ThreadDeleted.id)
    } else if (isArchived) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(BookmarksScreen.BookmarkAnnotatedContent.ThreadArchived.id)
    }

    if (isError) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(BookmarksScreen.BookmarkAnnotatedContent.ThreadError.id)
    }
  }
}

private fun dragKey(threadDescriptor: ThreadDescriptor) = "${bookmarkItemKey}_${threadDescriptor.asKey()}"