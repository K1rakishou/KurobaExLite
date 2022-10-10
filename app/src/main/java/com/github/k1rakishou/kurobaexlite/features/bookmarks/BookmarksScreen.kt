package com.github.k1rakishou.kurobaexlite.features.bookmarks

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.settings.application.AppSettingsScreen
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.image.GrayscaleTransformation
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.DrawerContentType
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.UiNavigationElement
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.LayoutOrientation
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SlotLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.rememberReorderState
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.time.Duration.Companion.milliseconds

class BookmarksScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val bookmarksScreenViewModel: BookmarksScreenViewModel by componentActivity.viewModel()

  private val bookmarksFloatingMenuItems: List<FloatingMenuItem> by lazy {
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
    val appSettings = koinRemember<AppSettings>()

    val coroutineScope = rememberCoroutineScope()

    ContentInternal(
      openAppSettings = { openAppSettings() },
      showBookmarkOptions = {
        coroutineScope.launch {
          showBookmarkOptions(
            context = context,
            drawerContentType = appSettings.drawerContentType.read()
          )
        }
      },
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
            key = BookmarksSnackbarButton.UndoThreadBookmarkDeletion,
            text = context.getString(R.string.undo),
            data = Pair(oldPosition, deletedBookmark)
          ),
          SnackbarContentItem.Spacer(space = 8.dp),
        )
      )
    )
  }

  private fun showBookmarkOptions(context: Context, drawerContentType: DrawerContentType) {
    val menuItems = when (drawerContentType) {
      DrawerContentType.History -> {
        return
      }
      DrawerContentType.Bookmarks -> bookmarksFloatingMenuItems
    }

    navigationRouter.presentScreen(
      FloatingMenuScreen(
        floatingMenuKey = FloatingMenuScreen.BOOKMARKS_OVERFLOW,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        menuItems = menuItems,
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

  enum class BookmarksSnackbarButton {
    UndoThreadBookmarkDeletion
  }

  enum class HistorySnackbarButton {
    UndoNavHistoryDeletion
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ContentInternal(
  openAppSettings: () -> Unit,
  showBookmarkOptions: () -> Unit,
  showRevertBookmarkDeletion: (ThreadBookmark, Int) -> Unit
) {
  val bookmarksScreenViewModel = koinRememberViewModel<BookmarksScreenViewModel>()
  val historyScreenViewModel = koinRememberViewModel<HistoryScreenViewModel>()
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val snackbarManager = koinRemember<SnackbarManager>()
  val appSettings = koinRemember<AppSettings>()

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
        if (snackbarClickable.key !is BookmarksScreen.BookmarksSnackbarButton) {
          return@collectLatest
        }

        when (snackbarClickable.key as BookmarksScreen.BookmarksSnackbarButton) {
          BookmarksScreen.BookmarksSnackbarButton.UndoThreadBookmarkDeletion -> {
            val pair = snackbarClickable.data as? Pair<Int, ThreadBookmark>
              ?: return@collectLatest

            val prevPosition = pair.first
            val threadBookmark = pair.second

            bookmarksScreenViewModel.undoBookmarkDeletion(threadBookmark, prevPosition)
          }
        }
      }
    })

  LaunchedEffect(
    key1 = Unit,
    block = {
      snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
        if (snackbarClickable.key !is BookmarksScreen.HistorySnackbarButton) {
          return@collectLatest
        }

        when (snackbarClickable.key as BookmarksScreen.HistorySnackbarButton) {
          BookmarksScreen.HistorySnackbarButton.UndoNavHistoryDeletion -> {
            val pair = snackbarClickable.data as? Pair<Int, UiNavigationElement>
              ?: return@collectLatest

            val prevIndex = pair.first
            val uiNavigationElement = pair.second

            historyScreenViewModel.undoNavElementDeletion(prevIndex, uiNavigationElement)
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

  val drawerContentType by appSettings.drawerContentType.listen().collectAsState(initial = null)

  GradientBackground {
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
          }
        )

        dynamic(
          weight = 1f,
          key = "ItemsList",
          content = {
            Box(modifier = Modifier.fillMaxSize()) {
              AnimatedContent(
                targetState = drawerContentType,
                transitionSpec = {
                  fadeIn(animationSpec = tween(220, delayMillis = 90)) with
                    fadeOut(animationSpec = tween(90))
                }
              ) { state ->
                when (state) {
                  null -> {
                    // no-op
                  }
                  DrawerContentType.History -> {
                    HistoryList()
                  }
                  DrawerContentType.Bookmarks -> {
                    BookmarksList(
                      pullToRefreshState = pullToRefreshState,
                      reorderableState = reorderableState,
                      showRevertBookmarkDeletion = showRevertBookmarkDeletion
                    )
                  }
                }
              }
            }
          }
        )
      }
    )
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
