package com.github.k1rakishou.kurobaexlite.features.posts.thread.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.ThreadScreenPostsState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarIcon
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ThreadScreenDefaultToolbar(
  private val bookmarksManager: BookmarksManager,
  private val threadScreenViewModel: ThreadScreenViewModel,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val globalUiInfoManager: GlobalUiInfoManager,
  private val onBackPressed: suspend () -> Unit,
  private val showLocalSearchToolbar: () -> Unit,
  private val toggleBookmarkState: () -> Unit,
  private val showOverflowMenu: () -> Unit,
  val state: State = State()
) : ChildToolbar() {

  override val toolbarKey: String = key

  @Composable
  override fun Content() {
    val screenContentLoaded by threadScreenViewModel.postScreenState.contentLoaded.collectAsState()

    val mainUiLayoutModeMut by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut

    if (mainUiLayoutMode == null) {
      return
    }

    UpdateThreadBookmarkIcon()

    LaunchedEffect(
      key1 = screenContentLoaded,
      block = {
        state.rightIcons.forEach { toolbarIcon ->
          if (toolbarIcon.key == State.Icons.Overflow) {
            return@forEach
          }

          toolbarIcon.visible.value = screenContentLoaded
        }
      }
    )

    UpdateToolbarTitle(
      parsedPostDataCache = parsedPostDataCache,
      postScreenState = threadScreenViewModel.postScreenState,
      catalogScreenDefaultToolbarState = { state }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        state.iconClickEvents.collect { icon ->
          when (icon) {
            State.Icons.Back -> {
              onBackPressed()
            }
            State.Icons.Search -> {
              showLocalSearchToolbar()
            }
            State.Icons.Bookmark -> {
              toggleBookmarkState()
            }
            State.Icons.Overflow -> {
              showOverflowMenu()
            }
          }
        }
      }
    )

    KurobaToolbarLayout(
      leftPart = {
        if (mainUiLayoutMode == MainUiLayoutMode.Phone) {
          state.leftIcon.Content(onClick = { key -> state.onIconClicked(key) })
        }
      },
      middlePart = {
        val toolbarTitle by state.toolbarTitleState
        val toolbarSubtitle by state.toolbarSubtitleState

        if (toolbarTitle != null) {
          Column(
            modifier = Modifier
              .fillMaxHeight()
              .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
          ) {
            Row {
              Text(
                text = toolbarTitle!!,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp
              )

              Spacer(modifier = Modifier.width(8.dp))
            }

            if (toolbarSubtitle != null) {
              Text(
                text = toolbarSubtitle!!,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
              )
            }
          }
        }
      },
      rightPart = {
        state.rightIcons.fastForEach { toolbarIcon ->
          toolbarIcon.Content(onClick = { key -> state.onIconClicked(key) })
        }
      }
    )
  }

  @Composable
  private fun UpdateToolbarTitle(
    parsedPostDataCache: ParsedPostDataCache,
    postScreenState: PostScreenState,
    catalogScreenDefaultToolbarState: () -> State?
  ) {
    val context = LocalContext.current
    val postListAsyncMut by postScreenState.postsAsyncDataState.collectAsState()
    val postListAsync = postListAsyncMut

    LaunchedEffect(
      key1 = postListAsync,
      block = {
        when (postListAsync) {
          AsyncData.Uninitialized -> {
            val state = catalogScreenDefaultToolbarState()
              ?: return@LaunchedEffect

            state.toolbarTitleState.value = null
          }
          AsyncData.Loading -> {
            val state = catalogScreenDefaultToolbarState()
              ?: return@LaunchedEffect

            state.toolbarTitleState.value =
              context.resources.getString(R.string.toolbar_loading_title)
          }
          is AsyncData.Error -> {
            val state = catalogScreenDefaultToolbarState()
              ?: return@LaunchedEffect

            state.toolbarTitleState.value =
              context.resources.getString(R.string.toolbar_loading_error)
          }
          is AsyncData.Data -> {
            when (val chanDescriptor = postListAsync.data.chanDescriptor) {
              is CatalogDescriptor -> {
                val state = catalogScreenDefaultToolbarState()
                  ?: return@LaunchedEffect

                state.toolbarTitleState.value =
                  parsedPostDataCache.formatCatalogToolbarTitle(chanDescriptor)
              }
              is ThreadDescriptor -> {
                snapshotFlow { (postScreenState as ThreadScreenPostsState).originalPostState.value }
                  .collect { originalPost ->
                    if (originalPost == null) {
                      return@collect
                    }

                    parsedPostDataCache.ensurePostDataLoaded(
                      isCatalog = false,
                      postDescriptor = originalPost.postDescriptor,
                      func = {
                        val state = catalogScreenDefaultToolbarState()
                          ?: return@ensurePostDataLoaded

                        val title = parsedPostDataCache.formatThreadToolbarTitle(originalPost.postDescriptor)
                          ?: return@ensurePostDataLoaded

                        state.toolbarTitleState.value = title
                      }
                    )
                  }
              }
            }
          }
        }
      })
  }

  @Composable
  private fun UpdateThreadBookmarkIcon() {
    LaunchedEffect(
      key1 = Unit,
      block = {
        threadScreenViewModel.currentlyOpenedThreadFlow.collect { currentlyOpenedThreadDescriptor ->
          val currentThreadDescriptor = threadScreenViewModel.threadDescriptor
            ?: return@collect

          if (currentlyOpenedThreadDescriptor != currentThreadDescriptor) {
            return@collect
          }

          val isThreadBookmarked = bookmarksManager.contains(currentThreadDescriptor)

          val bookmarkThreadIcon = state.bookmarkIcon()
          if (isThreadBookmarked) {
            bookmarkThreadIcon.drawableId.value = R.drawable.ic_baseline_bookmark_24
          } else {
            bookmarkThreadIcon.drawableId.value = R.drawable.ic_baseline_bookmark_border_24
          }
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        bookmarksManager.bookmarkEventsFlow.collect { event ->
          val currentThreadDescriptor = threadScreenViewModel.threadDescriptor

          when (event) {
            is BookmarksManager.Event.Created -> {
              val isForThisThread = event.threadDescriptors
                .any { threadDescriptor -> threadDescriptor == currentThreadDescriptor }

              if (!isForThisThread) {
                return@collect
              }

              val bookmarkThreadIcon = state.bookmarkIcon()
              bookmarkThreadIcon.drawableId.value = R.drawable.ic_baseline_bookmark_24
            }
            is BookmarksManager.Event.Deleted -> {
              val isForThisThread = event.threadDescriptors
                .any { threadDescriptor -> threadDescriptor == currentThreadDescriptor }

              if (!isForThisThread) {
                return@collect
              }

              val bookmarkThreadIcon = state.bookmarkIcon()
              bookmarkThreadIcon.drawableId.value = R.drawable.ic_baseline_bookmark_border_24
            }
            is BookmarksManager.Event.Updated -> {
              // no-op
            }
          }
        }
      }
    )
  }

  class State {
    val toolbarTitleState = mutableStateOf<String?>(null)
    val toolbarSubtitleState = mutableStateOf<String?>(null)

    val leftIcon = ToolbarIcon(
      key = Icons.Back,
      drawableId = R.drawable.ic_baseline_arrow_back_24
    )

    val rightIcons = listOf(
      ToolbarIcon(
        key = Icons.Search,
        drawableId = R.drawable.ic_baseline_search_24,
        visible = false
      ),
      ToolbarIcon(
        key = Icons.Bookmark,
        drawableId = R.drawable.ic_baseline_bookmark_border_24,
        visible = false
      ),
      ToolbarIcon(
        key = Icons.Overflow,
        drawableId = R.drawable.ic_baseline_more_vert_24
      ),
    )

    private val _iconClickEvents = MutableSharedFlow<Icons>(extraBufferCapacity = Channel.UNLIMITED)
    val iconClickEvents: SharedFlow<Icons>
      get() = _iconClickEvents.asSharedFlow()

    fun bookmarkIcon(): ToolbarIcon<Icons> {
      return rightIcons.first { icon -> icon.key == Icons.Bookmark }
    }

    fun onIconClicked(icons: Icons) {
      _iconClickEvents.tryEmit(icons)
    }

    enum class Icons {
      Back,
      Search,
      Bookmark,
      Overflow
    }
  }

  companion object {
    const val key = "ThreadScreenDefaultToolbar"
  }

}