package com.github.k1rakishou.kurobaexlite.features.posts.thread.toolbar

import android.os.Bundle
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.LocalMainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenDefaultToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ThreadScreenDefaultToolbar(
  private val threadScreenViewModel: ThreadScreenViewModel,
  private val onBackPressed: suspend () -> Unit,
  private val showLocalSearchToolbar: () -> Unit,
  private val toggleBookmarkState: suspend (Boolean) -> Unit,
  private val openThreadAlbum: () -> Unit,
  private val showOverflowMenu: () -> Unit,
) : PostsScreenDefaultToolbar<ThreadScreenDefaultToolbar.State>() {
  private val key = "ThreadScreenDefaultToolbar"
  private val state: State = State("${key}_state")

  override val toolbarState: ToolbarState = state
  override val toolbarKey: String = key

  @Composable
  override fun Content() {
    val screenContentLoaded by threadScreenViewModel.postScreenState.contentLoaded.collectAsState()
    val currentUiLayoutMode = LocalMainUiLayoutMode.current

    UpdateThreadBookmarkIcon(state)

    LaunchedEffect(
      key1 = screenContentLoaded,
      block = {
        state.rightIcons.forEach { toolbarIcon ->
          if (toolbarIcon.key == State.Icon.Overflow) {
            return@forEach
          }

          toolbarIcon.enabled.value = screenContentLoaded
        }
      }
    )

    UpdateToolbarTitle(
      isCatalogMode = false,
      postScreenState = threadScreenViewModel.postScreenState,
      defaultToolbarState = { state }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        state.iconClickEvents.collect { (icon, longClicked) ->
          when (icon) {
            State.Icon.Back -> {
              onBackPressed()
            }
            State.Icon.Search -> {
              showLocalSearchToolbar()
            }
            State.Icon.Bookmark -> {
              toggleBookmarkState(longClicked)
            }
            State.Icon.Album -> {
              openThreadAlbum()
            }
            State.Icon.Overflow -> {
              showOverflowMenu()
            }
          }
        }
      }
    )

    KurobaToolbarLayout(
      leftPart = {
        if (currentUiLayoutMode == MainUiLayoutMode.Phone) {
          state.leftIcon.Content(
            onClick = { key -> state.onIconClicked(key) },
            onLongClick = { key -> state.onIconLongClicked(key) }
          )
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
          toolbarIcon.Content(
            onClick = { key -> state.onIconClicked(key) },
            onLongClick = { key -> state.onIconLongClicked(key) }
          )
        }
      }
    )
  }

  @Stable
  class State(
    override val saveableComponentKey: String
  ) : PostsScreenDefaultToolbar.PostsScreenToolbarState() {

    val leftIcon = KurobaToolbarIcon(
      key = Icon.Back,
      drawableId = R.drawable.ic_baseline_arrow_back_24
    )

    val rightIcons = listOf(
      KurobaToolbarIcon(
        key = Icon.Search,
        drawableId = R.drawable.ic_baseline_search_24,
        enabled = false
      ),
      KurobaToolbarIcon(
        key = Icon.Bookmark,
        drawableId = R.drawable.ic_baseline_bookmark_border_24,
        enabled = false
      ),
      KurobaToolbarIcon(
        key = Icon.Album,
        drawableId = R.drawable.ic_baseline_image_24,
        enabled = false
      ),
      KurobaToolbarIcon(
        key = Icon.Overflow,
        drawableId = R.drawable.ic_baseline_more_vert_24
      ),
    )

    private val _iconClickEvents = MutableSharedFlow<IconClickEvent>(extraBufferCapacity = Channel.UNLIMITED)
    val iconClickEvents: SharedFlow<IconClickEvent>
      get() = _iconClickEvents.asSharedFlow()

    override fun saveState(): Bundle {
      return Bundle().apply {
        putString(TITLE_KEY, toolbarTitleState.value)
        putString(SUBTITLE_KEY, toolbarSubtitleState.value)
      }
    }

    override fun restoreFromState(bundle: Bundle?) {
      bundle?.getString(TITLE_KEY)?.let { title -> toolbarTitleState.value = title }
      bundle?.getString(SUBTITLE_KEY)?.let { subtitle -> toolbarSubtitleState.value = subtitle }
    }

    fun bookmarkIcon(): KurobaToolbarIcon<Icon> {
      return rightIcons.first { icon -> icon.key == Icon.Bookmark }
    }

    fun onIconClicked(icon: Icon) {
      _iconClickEvents.tryEmit(IconClickEvent(icon, false))
    }

    fun onIconLongClicked(icon: Icon) {
      _iconClickEvents.tryEmit(IconClickEvent(icon, true))
    }

    data class IconClickEvent(
      val icon: Icon,
      val longClicked: Boolean
    )

    enum class Icon {
      Back,
      Search,
      Bookmark,
      Album,
      Overflow
    }

    companion object {
      private const val TITLE_KEY = "title"
      private const val SUBTITLE_KEY = "subtitle"
    }
  }

}

@Composable
private fun UpdateThreadBookmarkIcon(state: ThreadScreenDefaultToolbar.State) {
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val bookmarksManager: BookmarksManager = koinRemember()

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
          is BookmarksManager.Event.Loaded,
          is BookmarksManager.Event.Updated -> {
            // no-op
          }
        }
      }
    }
  )
}