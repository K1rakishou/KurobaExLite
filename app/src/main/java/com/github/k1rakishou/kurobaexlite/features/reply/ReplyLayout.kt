package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import org.koin.core.context.GlobalContext

@Composable
fun ReplyLayoutContainer(
  replyLayoutState: ReplyLayoutState
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current
  val windowInsets = LocalWindowInsets.current
  val replyLayoutOpenedHeight = dimensionResource(id = R.dimen.reply_layout_opened_height)
  var maxAvailableHeight by remember { mutableStateOf<Int>(0) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { size -> maxAvailableHeight = size.height },
    contentAlignment = Alignment.BottomCenter
  ) {
    val replyLayoutVisibility by replyLayoutState.replyLayoutVisibilityState

    val replyLayoutTransitionState = remember {
      derivedStateOf {
        ReplyLayoutTransitionState(replyLayoutVisibility = replyLayoutVisibility)
      }
    }

    val replyLayoutTransition = updateTransition(
      targetState = replyLayoutTransitionState,
      label = "Reply layout transition animation"
    )

    val heightAnimated by replyLayoutTransition.animateDp(label = "height animation") { state ->
      when (state.value.replyLayoutVisibility) {
        ReplyLayoutVisibility.Closed,
        ReplyLayoutVisibility.Opened -> replyLayoutOpenedHeight
        ReplyLayoutVisibility.Expanded -> with(density) { maxAvailableHeight.toDp() }
      }
    }

    val offsetYAnimated by replyLayoutTransition.animateDp(label = "offset Y animation") { state ->
      when (state.value.replyLayoutVisibility) {
        ReplyLayoutVisibility.Closed -> replyLayoutOpenedHeight
        ReplyLayoutVisibility.Opened,
        ReplyLayoutVisibility.Expanded -> 0.dp
      }
    }

    Column(
      modifier = Modifier
        .offset(y = offsetYAnimated)
        .consumeClicks(enabled = true)
        .background(chanTheme.backColorSecondaryCompose)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(heightAnimated)
      ) {
        ReplyLayout()
      }

      if (replyLayoutVisibility != ReplyLayoutVisibility.Closed) {
        Spacer(modifier = Modifier.height(windowInsets.bottom))
      }
    }
  }
}

@Composable
private fun ReplyLayout() {
  KurobaComposeText(text = "Reply layout")
}

private class ReplyLayoutTransitionState(
  val replyLayoutVisibility: ReplyLayoutVisibility
)

class ReplyLayoutState(
  private val screenKey: ScreenKey
) {
  private val uiInfoManager: UiInfoManager by lazy { GlobalContext.get().get() }

  private val _replyLayoutVisibilityState = mutableStateOf(ReplyLayoutVisibility.Closed)
  val replyLayoutVisibilityState: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibilityState

  fun openReplyLayout() {
    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
    onReplyLayoutVisibilityStateChanged()
  }

  fun expandReplyLayout() {
    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Expanded
    onReplyLayoutVisibilityStateChanged()
  }

  fun onBackPressed(): Boolean {
    val currentState = replyLayoutVisibilityState.value
    if (currentState == ReplyLayoutVisibility.Closed) {
      return false
    }

    if (currentState == ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityStateChanged()
      return true
    }

    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Closed
    onReplyLayoutVisibilityStateChanged()
    return true
  }

  private fun onReplyLayoutVisibilityStateChanged() {
    uiInfoManager.replyLayoutVisibilityStateChanged(screenKey, _replyLayoutVisibilityState.value)
  }

}

enum class ReplyLayoutVisibility {
  Closed,
  Opened,
  Expanded
}