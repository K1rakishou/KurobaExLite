package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SimpleToolbarStateBuilder<T : Any> private constructor(
  private val context: Context
) {
  private var toolbarTitle: String? = null
  private var toolbarSubtitle: String? = null

  private var leftIcon: ToolbarIcon<T>? = null
  private val rightIcons = mutableListOf<ToolbarIcon<T>>()

  fun titleString(title: String): SimpleToolbarStateBuilder<T> {
    toolbarTitle = title
    return this
  }

  fun titleId(@StringRes id: Int): SimpleToolbarStateBuilder<T> {
    toolbarTitle = context.resources.getString(id)
    return this
  }

  fun subtitle(subtitle: String): SimpleToolbarStateBuilder<T> {
    toolbarSubtitle = subtitle
    return this
  }

  fun leftIcon(toolbarIcon: ToolbarIcon<T>): SimpleToolbarStateBuilder<T> {
    leftIcon = toolbarIcon
    return this
  }

  fun addRightIcon(toolbarIcon: ToolbarIcon<T>): SimpleToolbarStateBuilder<T> {
    rightIcons += toolbarIcon
    return this
  }

  fun build(): SimpleToolbarState<T> {
    return SimpleToolbarState(
      title = toolbarTitle,
      subtitle = toolbarSubtitle,
      _leftIcon = requireNotNull(leftIcon) { "Left icon is null!" },
      _rightIcons = rightIcons
    )
  }

  companion object {
    fun <T: Any> Builder(context: Context): SimpleToolbarStateBuilder<T> {
      return SimpleToolbarStateBuilder<T>(context)
    }
  }
}

class SimpleToolbarState<T : Any>(
  title: String?,
  subtitle: String?,
  _leftIcon: ToolbarIcon<T>,
  _rightIcons: List<ToolbarIcon<T>>
) {
  val toolbarTitleState = mutableStateOf<String?>(title)
  val toolbarSubtitleState = mutableStateOf<String?>(subtitle)

  val leftIcon = _leftIcon
  val rightIcons = _rightIcons


  private val _iconClickEvents = MutableSharedFlow<T>(extraBufferCapacity = Channel.UNLIMITED)
  val iconClickEvents: SharedFlow<T>
    get() = _iconClickEvents.asSharedFlow()

  fun onIconClicked(iconKey: T) {
    _iconClickEvents.tryEmit(iconKey)
  }

}

class SimpleToolbar<T : Any>(
  override val toolbarKey: String,
  val simpleToolbarState: SimpleToolbarState<T>
) : ChildToolbar() {

  @Composable
  override fun Content() {
    KurobaToolbarLayout(
      leftPart = {
        simpleToolbarState.leftIcon.Content(
          onClick = { key -> simpleToolbarState.onIconClicked(key) }
        )
      },
      middlePart = {
        val toolbarTitle by simpleToolbarState.toolbarTitleState
        val toolbarSubtitle by simpleToolbarState.toolbarSubtitleState

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
        simpleToolbarState.rightIcons.fastForEach { toolbarIcon ->
          toolbarIcon.Content(onClick = { key -> simpleToolbarState.onIconClicked(key) })
        }
      }
    )
  }

  companion object {
    const val key = "SimpleToolbar"
  }
}

class SimpleSearchToolbar(
  val onSearchQueryUpdated: (String?) -> Unit,
  val closeSearch: suspend () -> Unit
) : ChildToolbar() {

  override val toolbarKey: String = key

  override fun onDispose() {
    super.onDispose()

    onSearchQueryUpdated.invoke(null)
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun Content() {
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val searchDebouncer = remember { DebouncingCoroutineExecutor(coroutineScope) }

    val chanTheme = LocalChanTheme.current
    val parentBgColor = chanTheme.primaryColorCompose

    var searchQuery by remember { mutableStateOf(TextFieldValue()) }

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          keyboardController?.hide()
        }
      }
    )

    KurobaToolbarLayout(
      leftPart = {
        KurobaComposeIcon(
          modifier = Modifier
            .size(toolbarIconSize)
            .kurobaClickable(
              bounded = false,
              onClick = {
                coroutineScope.launch {
                  if (searchQuery.text.isNotEmpty()) {
                    searchQuery = TextFieldValue(text = "")
                    onSearchQueryUpdated.invoke("")
                  } else {
                    searchDebouncer.stop()
                    onSearchQueryUpdated.invoke(null)
                    closeSearch()
                  }
                }
              }
            ),
          drawableId = if (searchQuery.text.isNotEmpty()) {
            R.drawable.ic_baseline_clear_24
          } else {
            R.drawable.ic_baseline_arrow_back_24
          }
        )
      },
      middlePart = {
        KurobaComposeCustomTextField(
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .focusable(),
          value = searchQuery,
          labelText = stringResource(R.string.toolbar_type_to_search_hint),
          parentBackgroundColor = parentBgColor,
          onValueChange = { updatedQuery ->
            if (searchQuery != updatedQuery) {
              searchQuery = updatedQuery

              searchDebouncer.post(timeout = 125L) {
                onSearchQueryUpdated.invoke(updatedQuery.text)
              }
            }
          }
        )
      },
      rightPart = null
    )
  }

  companion object {
    const val key = "SimpleSearchToolbar"
  }
}