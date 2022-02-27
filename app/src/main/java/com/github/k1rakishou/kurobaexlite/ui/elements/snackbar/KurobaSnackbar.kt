package com.github.k1rakishou.kurobaexlite.ui.elements.snackbar

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive

@Composable
fun KurobaSnackbar(
  modifier: Modifier = Modifier,
  snackbarEventFlow: SharedFlow<SnackbarInfoEvent>,
  kurobaSnackbarState: KurobaSnackbarState = rememberKurobaSnackbarState()
) {
  val insets = LocalWindowInsets.current
  val chanTheme = LocalChanTheme.current

  LaunchedEffect(
    key1 = kurobaSnackbarState,
    block = {
      snackbarEventFlow.collect { snackbarInfoEvent ->
        when (snackbarInfoEvent) {
          is SnackbarInfoEvent.Pop -> {
            kurobaSnackbarState.popSnackbar(snackbarInfoEvent.id)
          }
          is SnackbarInfoEvent.Push -> {
            kurobaSnackbarState.pushSnackbar(snackbarInfoEvent.snackbarInfo)
          }
        }
      }
    })

  LaunchedEffect(
    key1 = kurobaSnackbarState,
    block = {
      while (isActive) {
        delay(250L)

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
    for ((index, snackbarInfo) in kurobaSnackbarState.activeSnackbars.withIndex()) {
      key(snackbarInfo.id) {
        val offsetDp = with(LocalDensity.current) { (index * (40.dp).toPx()).toDp() }

        KurobaComposeCardView(
          modifier = Modifier
            .offset(y = offsetDp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
          backgroundColor = chanTheme.backColorSecondaryCompose
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp)
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            KurobaSnackbarContent(snackbarInfo.content)
          }
        }
      }
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
fun rememberKurobaSnackbarState(keyTag: String? = null): KurobaSnackbarState {
  return remember { KurobaSnackbarState(keyTag) }
}

class KurobaSnackbarState(
  private val tag: String?
) {
  private val _activeSnackbars = mutableStateListOf<SnackbarInfo>()
  val activeSnackbars: List<SnackbarInfo>
    get() = _activeSnackbars

  fun pushSnackbar(snackbarInfo: SnackbarInfo) {
    val indexOfSnackbar = _activeSnackbars
      .indexOfFirst { snackbarInfo -> snackbarInfo.id == snackbarInfo.id }

    if (indexOfSnackbar < 0) {
      _activeSnackbars.add(snackbarInfo)
    }
  }

  fun popSnackbar(id: SnackbarId) {
    val indexOfSnackbar = _activeSnackbars
      .indexOfFirst { snackbarInfo -> snackbarInfo.id == id }

    if (indexOfSnackbar >= 0) {
      _activeSnackbars.removeAt(indexOfSnackbar)
    }
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

  fun removeOldSnackbars() {
    val currentTime = System.currentTimeMillis()

    for (activeSnackbar in _activeSnackbars) {
      if (activeSnackbar.aliveUntil != null && currentTime >= activeSnackbar.aliveUntil) {
        popSnackbar(activeSnackbar.id)
      }
    }
  }

}

sealed class SnackbarInfoEvent {
  data class Push(val snackbarInfo: SnackbarInfo) : SnackbarInfoEvent()
  data class Pop(val id: SnackbarId) : SnackbarInfoEvent()
}

class SnackbarInfo(
  val id: SnackbarId,
  val aliveUntil: Long?,
  val content: List<SnackbarContentItem>
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnackbarInfo

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}

sealed class SnackbarContentItem {
  object LoadingIndicator : SnackbarContentItem()
  data class Text(val text: String) : SnackbarContentItem()
  data class Spacer(val space: Dp) : SnackbarContentItem()
}

enum class SnackbarId(val id: String) {
  CatalogOrThreadPostsLoading("catalog_or_thread_posts_loading"),
  NewPosts("new_posts")
}