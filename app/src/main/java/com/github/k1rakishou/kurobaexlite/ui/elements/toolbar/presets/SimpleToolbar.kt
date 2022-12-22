package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SimpleToolbar<T : Any>(
  override val toolbarKey: String,
  val simpleToolbarState: SimpleToolbarState<T>
) : KurobaChildToolbar() {

  override val toolbarState: ToolbarState = simpleToolbarState

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
              KurobaToolbarText(
                text = toolbarTitle!!,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp
              )

              Spacer(modifier = Modifier.width(8.dp))
            }

            if (toolbarSubtitle != null) {
              KurobaToolbarText(
                text = toolbarSubtitle!!,
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
}

class SimpleToolbarStateBuilder<T : Any> private constructor(
  private val context: Context
) {
  private var toolbarTitle: String? = null
  private var toolbarSubtitle: String? = null

  private var leftIcon: KurobaToolbarIcon<T>? = null
  private val rightIcons = mutableListOf<KurobaToolbarIcon<T>>()

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

  fun leftIcon(toolbarIcon: KurobaToolbarIcon<T>): SimpleToolbarStateBuilder<T> {
    leftIcon = toolbarIcon
    return this
  }

  fun addRightIcon(toolbarIcon: KurobaToolbarIcon<T>): SimpleToolbarStateBuilder<T> {
    rightIcons += toolbarIcon
    return this
  }

  fun build(key: String): SimpleToolbarState<T> {
    return SimpleToolbarState(
      saveableComponentKey = key,
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

@Stable
open class SimpleToolbarState<ToolbarIcon : Any>(
  override val saveableComponentKey: String,
  title: String?,
  subtitle: String?,
  _leftIcon: KurobaToolbarIcon<ToolbarIcon>,
  _rightIcons: List<KurobaToolbarIcon<ToolbarIcon>>
) : KurobaChildToolbar.ToolbarState {
  val toolbarTitleState = mutableStateOf<String?>(title)
  val toolbarSubtitleState = mutableStateOf<String?>(subtitle)

  val leftIcon = _leftIcon
  val rightIcons = _rightIcons

  private val _iconClickEvents = MutableSharedFlow<ToolbarIcon>(extraBufferCapacity = Channel.UNLIMITED)
  val iconClickEvents: SharedFlow<ToolbarIcon>
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

  fun findIconByKey(key: ToolbarIcon) : KurobaToolbarIcon<ToolbarIcon>? {
    if (leftIcon.key == key) {
      return leftIcon
    }

    return rightIcons.firstOrNull { icon -> icon.key == key }
  }

  fun onIconClicked(iconKey: ToolbarIcon) {
    _iconClickEvents.tryEmit(iconKey)
  }

  companion object {
    private const val TITLE_KEY = "title"
    private const val SUBTITLE_KEY = "subtitle"
  }

}
