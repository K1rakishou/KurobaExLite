package com.github.k1rakishou.kurobaexlite.features.drawer

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.settings.application.AppSettingsScreen
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.image.GrayscaleTransformation
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.DrawerContentType
import com.github.k1rakishou.kurobaexlite.helpers.util.TaskType
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.rememberCoroutineTask
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.rememberReorderState
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.time.Duration.Companion.milliseconds

class DrawerScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val drawerScreenViewModel: DrawerScreenViewModel by componentActivity.viewModel()

  private val bookmarksFloatingMenuItems: List<FloatingMenuItem> by lazy {
    listOf(
      FloatingMenuItem.Text(
        menuItemKey = ToolbarMenuItems.PruneInactiveBookmarks,
        text = FloatingMenuItem.MenuItemText.Id(R.string.bookmark_screen_toolbar_prune_dead),
        subText = FloatingMenuItem.MenuItemText.Id(R.string.bookmark_screen_toolbar_prune_dead_description)
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
            coroutineScope = coroutineScope,
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

  private fun showBookmarkOptions(
    context: Context,
    coroutineScope: CoroutineScope,
    drawerContentType: DrawerContentType
  ) {
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
          coroutineScope.launch {
            when (menuItem.menuItemKey as ToolbarMenuItems) {
              ToolbarMenuItems.PruneInactiveBookmarks -> {
                onPruneInactiveBookmarksItemClicked(context)
              }
            }
          }
        }
      )
    )
  }

  private suspend fun onPruneInactiveBookmarksItemClicked(
    context: Context
  ) {
    val deadBookmarksCount = drawerScreenViewModel.countDeadBookmarks()
    if (deadBookmarksCount <= 0) {
      snackbarManager.toast(message = appResources.string(R.string.bookmark_screen_prune_dead_bookmarks_no_inactive_bookmarks))
      return
    }

    val description = appResources.string(
      R.string.bookmark_screen_prune_dead_bookmarks_no_inactive_bookmarks_description,
      deadBookmarksCount
    )

    navigationRouter.presentScreen(
      DialogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        params = DialogScreen.Params(
          title = DialogScreen.Text.Id(R.string.bookmark_screen_prune_dead_bookmarks_dialog),
          description = DialogScreen.Text.String(description),
          negativeButton = DialogScreen.DialogButton(
            buttonText = R.string.bookmark_screen_prune_dead_bookmarks_dialog_negative_button
          ),
          positiveButton = DialogScreen.PositiveDialogButton(
            buttonText = R.string.bookmark_screen_prune_dead_bookmarks_dialog_positive_button,
            isActionDangerous = true,
            onClick = {
              drawerScreenViewModel.pruneDeadBookmarks(
                onFinished = { deleteResult ->
                  if (deleteResult.isFailure) {
                    val errorMessage = deleteResult.exceptionOrThrow()
                      .errorMessageOrClassName(userReadable = true)

                    snackbarManager.errorToast(
                      message = context.resources.getString(
                        R.string.bookmark_screen_failed_to_delete_dead_bookmarks,
                        errorMessage
                      ),
                      screenKey = MainScreen.SCREEN_KEY
                    )
                  } else {
                    val deletedCount = deleteResult.getOrThrow()

                    if (deletedCount > 0) {
                      snackbarManager.toast(
                        message = context.resources.getString(
                          R.string.bookmark_screen_deleted_n_dead_bookmarks,
                          deletedCount
                        ),
                        screenKey = MainScreen.SCREEN_KEY
                      )
                    } else {
                      snackbarManager.toast(
                        message = context.resources.getString(R.string.bookmark_screen_no_dead_bookmarks_to_delete),
                        screenKey = MainScreen.SCREEN_KEY
                      )
                    }
                  }
                }
              )
            }
          )
        )
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
    val bookmarkedThreadHasPosts: Boolean,
    val newPostsAnimated: Int,
    val newQuotesAnimated: Int,
    val totalPostsAnimated: Int,
    val watching: Boolean,
    val isFirstFetch: Boolean,
    val totalPages: Int,
    val currentPage: Int,
    val isBumpLimit: Boolean,
    val isImageLimit: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val isClosed: Boolean,
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
    ThreadError("id_thread_error"),
    ThreadClosed("id_thread_closed");

    companion object {
      internal val circleCropTransformation = CircleCropTransformation()
      internal val grayscaleTransformation = GrayscaleTransformation()

      internal val deleteBookmarkIconWidth = 40.dp
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
            ThreadClosed -> R.drawable.closed_icon
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
  val drawerScreenViewModel = koinRememberViewModel<DrawerScreenViewModel>()
  val historyScreenViewModel = koinRememberViewModel<HistoryScreenViewModel>()
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val snackbarManager = koinRemember<SnackbarManager>()
  val appSettings = koinRemember<AppSettings>()

  val windowInsets = LocalWindowInsets.current
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
        if (snackbarClickable.key !is DrawerScreen.BookmarksSnackbarButton) {
          return@collectLatest
        }

        when (snackbarClickable.key as DrawerScreen.BookmarksSnackbarButton) {
          DrawerScreen.BookmarksSnackbarButton.UndoThreadBookmarkDeletion -> {
            val pair = snackbarClickable.data as? Pair<Int, ThreadBookmark>
              ?: return@collectLatest

            val prevPosition = pair.first
            val threadBookmark = pair.second

            drawerScreenViewModel.undoBookmarkDeletion(threadBookmark, prevPosition)
          }
        }
      }
    })

  LaunchedEffect(
    key1 = Unit,
    block = {
      snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
        if (snackbarClickable.key !is DrawerScreen.HistorySnackbarButton) {
          return@collectLatest
        }

        when (snackbarClickable.key as DrawerScreen.HistorySnackbarButton) {
          DrawerScreen.HistorySnackbarButton.UndoNavHistoryDeletion -> {
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
      onDispose { drawerScreenViewModel.clearMarkedBookmarks() }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      drawerScreenViewModel.backgroundWatcherEventsFlow.collect {
        pullToRefreshState.stopRefreshing()
      }
    }
  )

  val drawerContentType by appSettings.drawerContentType.listen().collectAsState(initial = null)

  var searchQuery by rememberSaveable(
    key = "search_query",
    stateSaver = TextFieldValue.Saver
  ) { mutableStateOf<TextFieldValue>(TextFieldValue()) }

  val iconSize = 36.dp

  GradientBackground(modifier = Modifier.consumeClicks()) {
    Column(modifier = Modifier.fillMaxSize()) {
      Spacer(modifier = Modifier.height(windowInsets.top))

      Row(verticalAlignment = Alignment.CenterVertically) {
        DrawerSearchInput(
          modifier = Modifier
            .weight(1f)
            .wrapContentHeight(),
          searchQuery = searchQuery,
          searchingBookmarks = drawerContentType == DrawerContentType.Bookmarks,
          onSearchQueryChanged = { query -> searchQuery = query },
          onClearSearchQueryClicked = { searchQuery = TextFieldValue() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        DrawerContentTypeToggleIcon(iconSize = iconSize)

        Spacer(modifier = Modifier.width(8.dp))

        DrawerThemeToggleIcon(appSettings, iconSize)

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeIcon(
          modifier = Modifier
            .size(iconSize)
            .kurobaClickable(bounded = false, onClick = { openAppSettings() }),
          drawableId = R.drawable.ic_baseline_settings_24
        )

        KurobaComposeIcon(
          modifier = Modifier
            .size(iconSize)
            .kurobaClickable(bounded = false, onClick = { showBookmarkOptions() }),
          drawableId = R.drawable.ic_baseline_more_vert_24
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
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
              HistoryList(
                searchQuery = searchQuery.text
              )
            }
            DrawerContentType.Bookmarks -> {
              BookmarksList(
                searchQuery = searchQuery.text,
                pullToRefreshState = pullToRefreshState,
                reorderableState = reorderableState,
                showRevertBookmarkDeletion = showRevertBookmarkDeletion
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DrawerContentTypeToggleIcon(
  iconSize: Dp
) {
  val appSettings = koinRemember<AppSettings>()

  val drawerContentTypeMut by appSettings.drawerContentType.listen().collectAsState(initial = null)
  val drawerContentType = drawerContentTypeMut

  if (drawerContentType == null) {
    return
  }

  val coroutineTask = rememberCoroutineTask(taskType = TaskType.SingleInstance)

  val drawableId = when (drawerContentType) {
    DrawerContentType.History -> R.drawable.ic_baseline_bookmark_border_24
    DrawerContentType.Bookmarks -> R.drawable.ic_baseline_history_24
  }

  KurobaComposeClickableIcon(
    modifier = Modifier.size(iconSize),
    drawableId = drawableId,
    enabled = !coroutineTask.isRunning,
    onClick = {
      coroutineTask.launch {
        val newDrawerContentType = when (drawerContentType) {
          DrawerContentType.History -> DrawerContentType.Bookmarks
          DrawerContentType.Bookmarks -> DrawerContentType.History
        }

        appSettings.drawerContentType.write(newDrawerContentType)
      }
    }
  )
}

@Composable
private fun DrawerThemeToggleIcon(
  appSettings: AppSettings,
  iconSize: Dp
) {
  val themeEngine = koinRemember<ThemeEngine>()

  val isDarkThemeUsedMut by appSettings.isDarkThemeUsed.listen().collectAsState(initial = null)
  val isDarkThemeUsed = isDarkThemeUsedMut

  if (isDarkThemeUsed == null) {
    return
  }

  val iconId = if (isDarkThemeUsed) {
    R.drawable.ic_baseline_light_mode_24
  } else {
    R.drawable.ic_baseline_dark_mode_24
  }

  KurobaComposeIcon(
    modifier = Modifier
      .size(iconSize)
      .kurobaClickable(bounded = false, onClick = { themeEngine.toggleTheme() }),
    drawableId = iconId
  )
}