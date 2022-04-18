package com.github.k1rakishou.kurobaexlite.ui.elements.snackbar

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.helpers.ensureSingleElement
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.mutableIteration
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import java.util.Locale
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.core.context.GlobalContext

@Composable
fun KurobaSnackbarContainer(
  modifier: Modifier = Modifier,
  screenKey: ScreenKey,
  kurobaSnackbarState: KurobaSnackbarState
) {
  val uiInfoManager = koinRemember<UiInfoManager>()
  val snackbarManager = koinRemember<SnackbarManager>()

  val insets = LocalWindowInsets.current
  val chanTheme = LocalChanTheme.current

  val isTablet = uiInfoManager.isTablet
  val maxSnackbarWidth = if (isTablet) 600.dp else 400.dp

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
        delay(100L)

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
      )
      .requiredWidthIn(max = maxSnackbarWidth),
    contentAlignment = Alignment.BottomCenter
  ) {
    val activeSnackbars = kurobaSnackbarState.activeSnackbars

    SubcomposeLayout(
      measurePolicy = { constraints ->
        if (activeSnackbars.isEmpty()) {
          return@SubcomposeLayout layout(0, 0) { /*no-op*/ }
        }

        val measurables = arrayOfNulls<Measurable>(activeSnackbars.size)
        val placeables = arrayOfNulls<Placeable>(activeSnackbars.size)

        for ((index, snackbarInfo) in activeSnackbars.withIndex()) {
          val measurable = subcompose(
            slotId = snackbarInfo.snackbarId,
            content = {
              KurobaSnackbarLayout(
                isTablet = isTablet,
                chanTheme = chanTheme,
                snackbarInfo = snackbarInfo,
                snackbarManager = snackbarManager,
                dismissSnackbar = { snackbarId -> snackbarManager.popSnackbar(snackbarId) }
              )
            }
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
  isTablet: Boolean,
  chanTheme: ChanTheme,
  snackbarInfo: SnackbarInfo,
  snackbarManager: SnackbarManager,
  dismissSnackbar: (SnackbarId) -> Unit
) {
  val isToast = snackbarInfo.isToast

  val containerHorizPadding = if (isTablet) 14.dp else 10.dp
  val containerVertPadding = if (isTablet) 10.dp else 6.dp

  var contentHorizPadding = if (isTablet) 10.dp else 6.dp
  var contentVertPadding = if (isTablet) 14.dp else 8.dp

  if (isToast) {
    contentHorizPadding *= 1.5f
    contentVertPadding *= 1.25f
  }

  val backgroundColor = if (isToast) {
    Color.White
  } else {
    chanTheme.backColorSecondaryCompose
  }

  KurobaComposeCardView(
    modifier = Modifier
      .padding(
        horizontal = containerHorizPadding,
        vertical = containerVertPadding
      )
      .wrapContentWidth()
      .kurobaClickable(
        onClick = {
          if (snackbarInfo.hasClickableItems) {
            return@kurobaClickable
          }

          dismissSnackbar(snackbarInfo.snackbarId)
        }
      ),
    backgroundColor = backgroundColor
  ) {
    Row(
      modifier = Modifier
        .wrapContentHeight()
        .padding(
          horizontal = contentHorizPadding,
          vertical = contentVertPadding
        ),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaSnackbarContent(
        isTablet = isTablet,
        isToast = isToast,
        hasClickableItems = snackbarInfo.hasClickableItems,
        aliveUntil = snackbarInfo.aliveUntil,
        snackbarId = snackbarInfo.snackbarId,
        content = snackbarInfo.content,
        onSnackbarClicked = { snackbarClickable, snackbarId ->
          snackbarManager.onSnackbarElementClicked(snackbarClickable)
          dismissSnackbar(snackbarId)
        }
      )
    }
  }
}

@Composable
private fun RowScope.KurobaSnackbarContent(
  isTablet: Boolean,
  isToast: Boolean,
  hasClickableItems: Boolean,
  aliveUntil: Long?,
  content: List<SnackbarContentItem>,
  snackbarId: SnackbarId,
  onSnackbarClicked: (SnackbarClickable, SnackbarId) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val textSize = if (isTablet) 18.sp else 16.sp

  if (!isToast && hasClickableItems && aliveUntil != null) {
    val startTime = remember { SystemClock.elapsedRealtime() }
    var progress by remember { mutableStateOf(1f) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        val timeDelta = aliveUntil - startTime

        while (true) {
          val currentTimeDelta = aliveUntil - SystemClock.elapsedRealtime()
          if (currentTimeDelta < 0) {
            break
          }

          progress = currentTimeDelta.toFloat() / timeDelta.toFloat()
          delay(16 * 5)
        }

        progress = 0f
      }
    )

    KurobaComposeLoadingIndicator(
      progress = progress,
      modifier = Modifier.wrapContentSize(),
      indicatorSize = 24.dp
    )

    Spacer(modifier = Modifier.width(8.dp))
  }

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
        val widthModifier = if (isToast || !snackbarContentItem.takeWholeWidth) {
          Modifier.wrapContentWidth()
        } else {
          Modifier.weight(1f)
        }

        val textColor = when {
          snackbarContentItem.textColor != null -> snackbarContentItem.textColor
          isToast -> Color.Black
          else -> null
        }

        KurobaComposeText(
          modifier = widthModifier,
          fontSize = textSize,
          color = textColor,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          text = snackbarContentItem.formattedText
        )
      }
      is SnackbarContentItem.Button -> {
        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          text = snackbarContentItem.formattedText,
          customTextColor = snackbarContentItem.textColor ?: chanTheme.accentColorCompose,
          onClick = { onSnackbarClicked(snackbarContentItem, snackbarId) }
        )
      }
    }
  }
}

@Composable
fun rememberKurobaSnackbarState(
  keyTag: String? = null,
  maxSnackbarsAtTime: Int = 5
): KurobaSnackbarState {
  return remember {
    KurobaSnackbarState(
      tag = keyTag,
      maxSnackbarsAtTime = maxSnackbarsAtTime
    )
  }
}

class KurobaSnackbarState(
  private val tag: String?,
  private val maxSnackbarsAtTime: Int
) {
  private val snackbarManager: SnackbarManager by lazy { GlobalContext.get().get() }

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

@Stable
class SnackbarInfo(
  val snackbarId: SnackbarId,
  val createdAt: Long = SystemClock.elapsedRealtime(),
  val aliveUntil: Long?,
  val screenKey: ScreenKey = MainScreen.SCREEN_KEY,
  val content: List<SnackbarContentItem>,
  val isToast: Boolean = false
) {

  val hasClickableItems: Boolean
    get() = content.any { contentItem -> contentItem is SnackbarClickable }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnackbarInfo

    if (snackbarId != other.snackbarId) return false
    if (createdAt != other.createdAt) return false
    if (aliveUntil != other.aliveUntil) return false
    if (screenKey != other.screenKey) return false
    if (isToast != other.isToast) return false

    return true
  }

  override fun hashCode(): Int {
    var result = snackbarId.hashCode()
    result = 31 * result + createdAt.hashCode()
    result = 31 * result + (aliveUntil?.hashCode() ?: 0)
    result = 31 * result + screenKey.hashCode()
    result = 31 * result + isToast.hashCode()
    return result
  }

  companion object {
    fun snackbarDuration(duration: Duration): Long {
      return SystemClock.elapsedRealtime() + duration.inWholeMilliseconds
    }
  }

}

interface SnackbarClickable {
  val key: Any
  val data: Any?
}

@Stable
sealed class SnackbarContentItem {
  object LoadingIndicator : SnackbarContentItem()

  data class Text(
    private val text: String,
    val textColor: Color? = null,
    val takeWholeWidth: Boolean = true
  ) : SnackbarContentItem() {
    val formattedText by lazy { text.filter { ch -> ch != '\n' } }
  }

  data class Button(
    override val key: Any,
    override val data: Any? = null,
    private val text: String,
    val textColor: Color? = null,
  ) : SnackbarContentItem(), SnackbarClickable {
    val formattedText by lazy { text.uppercase(Locale.ENGLISH) }
  }

  data class Spacer(val space: Dp) : SnackbarContentItem()
}

sealed class SnackbarId(
  val id: String
) {
  object CatalogOrThreadPostsLoading : SnackbarId("catalog_or_thread_posts_loading")
  object NewPosts : SnackbarId("new_posts")
  object NavHistoryElementRemoved : SnackbarId("nav_history_element_removed")

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