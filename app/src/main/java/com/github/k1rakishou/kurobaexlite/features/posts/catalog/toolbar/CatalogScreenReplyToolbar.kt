package com.github.k1rakishou.kurobaexlite.features.posts.catalog.toolbar

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeThemeDependantText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CatalogScreenReplyToolbar(
  private val threadScreenViewModel: ThreadScreenViewModel,
  private val closeReplyLayout: () -> Unit,
  private val pickLocalFile: () -> Unit,
  private val imageRemoteSearch: () -> Unit,
) : KurobaChildToolbar() {
  private val key = "CatalogScreenReplyToolbar"
  private val state: State = State("${key}_state")

  override val toolbarState: ToolbarState = state
  override val toolbarKey: String = key

  @Composable
  override fun Content() {
    val context = LocalContext.current

    val currentCatalogDescriptorMut by threadScreenViewModel.currentlyOpenedCatalogFlow.collectAsState()
    val currentCatalogDescriptor = currentCatalogDescriptorMut

    LaunchedEffect(
      key1 = currentCatalogDescriptor,
      block = {
        if (currentCatalogDescriptor == null) {
          return@LaunchedEffect
        }

        state.toolbarTitleState.value = context.resources.getString(
          R.string.catalog_new_thread_on_board,
          currentCatalogDescriptor.boardCode
        )
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        state.iconClickEvents.collect { key ->
          when (key) {
            State.Icons.Close -> closeReplyLayout()
            State.Icons.PickLocalFile -> pickLocalFile()
            State.Icons.ImageRemoteSearch -> imageRemoteSearch()
          }
        }
      }
    )

    KurobaToolbarLayout(
      leftPart = {
        state.leftIcon.Content(onClick = { key -> state.onIconClicked(key) })
      },
      middlePart = {
        val toolbarTitleMut by state.toolbarTitleState
        val toolbarTitle = toolbarTitleMut

        if (toolbarTitle != null) {
          Column(
            modifier = Modifier
              .fillMaxHeight()
              .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row {
              KurobaComposeThemeDependantText(
                text = toolbarTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp
              )

              Spacer(modifier = Modifier.width(8.dp))
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

  class State(
    override val saveableComponentKey: String
  ) : ToolbarState {
    val toolbarTitleState = mutableStateOf<String?>(null)

    val leftIcon = KurobaToolbarIcon(
      key = Icons.Close,
      drawableId = R.drawable.ic_baseline_close_24
    )

    val rightIcons = listOf(
      KurobaToolbarIcon(
        key = Icons.PickLocalFile,
        drawableId = R.drawable.ic_baseline_attach_file_24
      ),
      KurobaToolbarIcon(
        key = Icons.ImageRemoteSearch,
        drawableId = R.drawable.ic_baseline_search_24
      ),
    )

    private val _iconClickEvents = MutableSharedFlow<Icons>(extraBufferCapacity = Channel.UNLIMITED)
    val iconClickEvents: SharedFlow<Icons>
      get() = _iconClickEvents.asSharedFlow()

    override fun saveState(): Bundle {
      return Bundle().apply {
        putString(TITLE_KEY, toolbarTitleState.value)
      }
    }

    override fun restoreFromState(bundle: Bundle?) {
      bundle?.getString(TITLE_KEY)?.let { title -> toolbarTitleState.value = title }
    }

    fun onIconClicked(icons: State.Icons) {
      _iconClickEvents.tryEmit(icons)
    }

    enum class Icons {
      Close,
      PickLocalFile,
      ImageRemoteSearch
    }

    companion object {
      private const val TITLE_KEY = "title"
    }
  }

}