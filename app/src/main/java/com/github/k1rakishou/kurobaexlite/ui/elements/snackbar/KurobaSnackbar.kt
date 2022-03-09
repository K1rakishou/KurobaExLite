package com.github.k1rakishou.kurobaexlite.ui.elements.snackbar

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.ensureSingleElement
import com.github.k1rakishou.kurobaexlite.helpers.mutableIteration
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.main.MainScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun KurobaSnackbarContainer(
  modifier: Modifier = Modifier,
  screenKey: ScreenKey,
  snackbarManager: SnackbarManager,
  kurobaSnackbarState: KurobaSnackbarState
) {
  val insets = LocalWindowInsets.current
  val chanTheme = LocalChanTheme.current

  LaunchedEffect(
    key1 = kurobaSnackbarState,
    block = {
      snackbarManager.snackbarEventFlow.collect { snackbarInfoEvent ->
        when (snackbarInfoEvent) {
          is SnackbarInfoEvent.Push -> {
            val evenScreenKey = snackbarInfoEvent.snackbarInfo.screenKey
            if (evenScreenKey == screenKey) {
              kurobaSnackbarState.pushSnackbar(snackbarInfoEvent.snackbarInfo)
            }
          }
          is SnackbarInfoEvent.Pop -> {
            kurobaSnackbarState.popSnackbar(snackbarInfoEvent.id)
          }
        }
      }
    })

  LaunchedEffect(
    key1 = kurobaSnackbarState,
    block = {
      while (isActive) {
        delay(1000L)

        kurobaSnackbarState.removeOldSnackbars()
      }
    })

  Box(
    modifier = modifier
      .padding(
        start = insets.left,
        end = insets.right,
        top = insets.top,
        bottom = insets.bottom
      ),
    contentAlignment = Alignment.BottomCenter
  ) {
    val activeSnackbars = kurobaSnackbarState.activeSnackbars

    SubcomposeLayout(
      measurePolicy = { constraints ->
        val measurables = arrayOfNulls<Measurable>(activeSnackbars.size)
        val placeables = arrayOfNulls<Placeable>(activeSnackbars.size)

        for ((index, snackbarInfo) in activeSnackbars.withIndex()) {
          val measurable = subcompose(
            slotId = snackbarInfo.snackbarId,
            content = { KurobaSnackbarLayout(chanTheme, snackbarInfo) }
          ).ensureSingleElement()

          measurables[index] = measurable
        }

        for ((index, measurable) in measurables.withIndex()) {
          val placeable = measurable!!.measure(constraints)
          placeables[index] = placeable
        }

        val maxWidth = placeables.fold(0f) { acc, placeable -> Math.max(acc, placeable!!.width.toFloat()) }
        val totalHeight = placeables.fold(0f) { acc, placeable -> acc + placeable!!.height.toFloat() }

        return@SubcomposeLayout layout(maxWidth.toInt(), totalHeight.toInt()) {
          var takenHeight = 0

          for (placeable in placeables) {
            if (placeable == null) {
              continue
            }

            placeable.placeRelative(0, takenHeight)

            takenHeight += placeable.height
          }
        }
      }
    )
  }
}

@Composable
private fun KurobaSnackbarLayout(
  chanTheme: ChanTheme,
  snackbarInfo: SnackbarInfo
) {
  KurobaComposeCardView(
    modifier = Modifier
      .padding(horizontal = 8.dp, vertical = 4.dp),
    backgroundColor = chanTheme.backColorSecondaryCompose
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 4.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaSnackbarContent(snackbarInfo.content)
    }
  }
}

@Composable
private fun RowScope.KurobaSnackbarContent(content: List<SnackbarContentItem>) {
  for (snackbarContentItem in content) {
    when (snackbarContentItem) {
      SnackbarContentItem.LoadingIndicator -> {
        KurobaComposeLoadingIndicator(
          modifier = Modifier.wrapContentSize(),
          indicatorSize = 24.dp
        )
      }
      is SnackbarContentItem.Spacer -> {
        Spacer(modifier = Modifier.width(snackbarContentItem.space))
      }
      is SnackbarContentItem.Text -> {
        KurobaComposeText(
          modifier = Modifier.weight(1f),
          text = snackbarContentItem.text
        )
      }
    }
  }
}

@Composable
fun rememberKurobaSnackbarState(
  snackbarManager: SnackbarManager,
  keyTag: String? = null,
  maxSnackbarsAtTime: Int = 5
): KurobaSnackbarState {
  return remember {
    KurobaSnackbarState(
      tag = keyTag,
      maxSnackbarsAtTime = maxSnackbarsAtTime,
      snackbarManager = snackbarManager
    )
  }
}

class KurobaSnackbarState(
  private val tag: String?,
  private val maxSnackbarsAtTime: Int,
  private val snackbarManager: SnackbarManager
) {
  private val _activeSnackbars = mutableStateListOf<SnackbarInfo>()
  val activeSnackbars: List<SnackbarInfo>
    get() = _activeSnackbars

  fun pushSnackbar(snackbarInfo: SnackbarInfo) {
    val indexOfSnackbar = _activeSnackbars
      .indexOfFirst { info -> info.snackbarId == snackbarInfo.snackbarId }

    if (indexOfSnackbar < 0) {
      _activeSnackbars.add(snackbarInfo)
      updateSnackbarsCount()
    } else {
      _activeSnackbars[indexOfSnackbar] = snackbarInfo
    }
  }

  fun popSnackbar(id: SnackbarId) {
    val indexOfSnackbar = _activeSnackbars
      .indexOfFirst { snackbarInfo -> snackbarInfo.snackbarId == id }

    if (indexOfSnackbar >= 0) {
      _activeSnackbars.removeAt(indexOfSnackbar)
      updateSnackbarsCount()
    }
  }

  fun removeOldSnackbars() {
    val currentTime = SystemClock.elapsedRealtime()
    var anythingRemoved = false

    _activeSnackbars.mutableIteration { mutableIterator, activeSnackbar ->
      if (
        activeSnackbar.aliveUntil != null
        && (currentTime >= activeSnackbar.aliveUntil || _activeSnackbars.size > maxSnackbarsAtTime)
      ) {
        anythingRemoved = true
        mutableIterator.remove()
      }

      return@mutableIteration true
    }

    if (anythingRemoved) {
      updateSnackbarsCount()
    }
  }

  private fun updateSnackbarsCount() {
    val snackbarsByScreenKey = _activeSnackbars.groupBy { it.screenKey }
    snackbarManager.onSnackbarsUpdated(snackbarsByScreenKey)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KurobaSnackbarState

    if (tag != other.tag) return false

    return true
  }

  override fun hashCode(): Int {
    return tag?.hashCode() ?: 0
  }

}

sealed class SnackbarInfoEvent {
  data class Push(val snackbarInfo: SnackbarInfo) : SnackbarInfoEvent()
  data class Pop(val id: SnackbarId) : SnackbarInfoEvent()
}

class SnackbarInfo(
  val snackbarId: SnackbarId,
  val aliveUntil: Long?,
  val screenKey: ScreenKey = MainScreen.SCREEN_KEY,
  val content: List<SnackbarContentItem>
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnackbarInfo

    if (snackbarId != other.snackbarId) return false
    if (aliveUntil != other.aliveUntil) return false
    if (screenKey != other.screenKey) return false

    return true
  }

  override fun hashCode(): Int {
    var result = snackbarId.hashCode()
    result = 31 * result + (aliveUntil?.hashCode() ?: 0)
    result = 31 * result + screenKey.hashCode()
    return result
  }
}

sealed class SnackbarContentItem {
  object LoadingIndicator : SnackbarContentItem()
  data class Text(val text: String) : SnackbarContentItem()
  data class Spacer(val space: Dp) : SnackbarContentItem()
}

sealed class SnackbarId(
  val id: String
) {
  object CatalogOrThreadPostsLoading : SnackbarId("catalog_or_thread_posts_loading")
  object NewPosts : SnackbarId("new_posts")

  class Toast(toastId: String) : SnackbarId(toastId)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnackbarId

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

}