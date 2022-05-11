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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarIcon
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ThreadScreenReplyToolbar(
  private val threadScreenViewModel: ThreadScreenViewModel,
  private val closeReplyLayout: () -> Unit,
  private val pickLocalFile: () -> Unit,
  val state: State = State()
) : ChildToolbar() {

  override val toolbarKey: String = key

  @Composable
  override fun Content() {
    val context = LocalContext.current

    val currentThreadDescriptorMut by threadScreenViewModel.currentlyOpenedThreadFlow.collectAsState()
    val currentThreadDescriptor = currentThreadDescriptorMut

    LaunchedEffect(
      key1 = currentThreadDescriptor,
      block = {
        if (currentThreadDescriptor == null) {
          return@LaunchedEffect
        }

        state.toolbarTitleState.value = context.resources.getString(
          R.string.thread_new_reply,
          currentThreadDescriptor.threadNo
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
              Text(
                text = toolbarTitle,
                color = Color.White,
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

  class State {
    val toolbarTitleState = mutableStateOf<String?>(null)

    val leftIcon = ToolbarIcon(
      key = Icons.Close,
      drawableId = R.drawable.ic_baseline_close_24
    )

    val rightIcons = listOf(
      ToolbarIcon(
        key = Icons.PickLocalFile,
        drawableId = R.drawable.ic_baseline_attach_file_24
      ),
    )

    private val _iconClickEvents = MutableSharedFlow<Icons>(extraBufferCapacity = Channel.UNLIMITED)
    val iconClickEvents: SharedFlow<Icons>
      get() = _iconClickEvents.asSharedFlow()

    fun onIconClicked(icons: State.Icons) {
      _iconClickEvents.tryEmit(icons)
    }

    enum class Icons {
      Close,
      PickLocalFile
    }
  }

  companion object {
    const val key = "ThreadScreenReplyToolbar"
  }
}