package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable

@Composable
fun ReplyLayoutContainer(
  chanDescriptor: ChanDescriptor?,
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current
  val windowInsets = LocalWindowInsets.current
  val sendReplyState by replyLayoutState.sendReplyState
  val replyLayoutContainerOpenedHeightDefault = dimensionResource(id = R.dimen.reply_layout_container_opened_height)
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  var maxAvailableHeight by remember { mutableStateOf<Int>(0) }

  LaunchedEffect(
    key1 = chanDescriptor,
    block = {
      replyLayoutState.lastErrorMessageFlow.collect { errorMessage ->
        if (chanDescriptor == null) {
          return@collect
        }

        replyLayoutViewModel.showErrorToast(chanDescriptor, errorMessage)
      }
    }
  )

  val replyLayoutEnabled = remember(key1 = sendReplyState, key2 = chanDescriptor) {
    if (chanDescriptor == null) {
      return@remember false
    }

    return@remember when (sendReplyState) {
      SendReplyState.Started -> false
      is SendReplyState.Finished -> true
    }
  }

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

    val replyLayoutContainerTransition = updateTransition(
      targetState = replyLayoutTransitionState,
      label = "Reply layout container transition animation"
    )

    val heightAnimated by replyLayoutContainerTransition.animateDp(
      label = "Reply layout container height animation",
      transitionSpec = {
         spring(visibilityThreshold = Dp.VisibilityThreshold)
      }
    ) { state ->
      when (state.value.replyLayoutVisibility) {
        ReplyLayoutVisibility.Closed,
        ReplyLayoutVisibility.Opened -> replyLayoutContainerOpenedHeightDefault
        ReplyLayoutVisibility.Expanded -> with(density) { maxAvailableHeight.toDp() }
      }
    }

    val offsetYAnimated by replyLayoutContainerTransition.animateDp(
      label = "Reply layout container offset Y animation",
      transitionSpec = {
        spring(visibilityThreshold = Dp.VisibilityThreshold)
      }
    ) { state ->
      when (state.value.replyLayoutVisibility) {
        ReplyLayoutVisibility.Closed -> replyLayoutContainerOpenedHeightDefault
        ReplyLayoutVisibility.Opened,
        ReplyLayoutVisibility.Expanded -> 0.dp
      }
    }

    Column(
      modifier = Modifier
        .offset(y = offsetYAnimated)
        .consumeClicks(enabled = true)
        .background(chanTheme.backColorCompose)
    ) {

      if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
        Spacer(modifier = Modifier.height(windowInsets.top + toolbarHeight))
      } else {
        KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
      }

      val replyLayoutHeightClampedDp = if (replyLayoutVisibility == ReplyLayoutVisibility.Closed) {
        heightAnimated
      } else {
        with(density) {
          val heightAnimatedPx = heightAnimated.toPx()
          val maxAllowedHeightPx = maxAvailableHeight - (windowInsets.bottom + windowInsets.top + toolbarHeight).toPx()

          return@with heightAnimatedPx.coerceAtMost(maxAllowedHeightPx).toDp()
        }
      }

      ReplyLayout(
        replyLayoutEnabled = replyLayoutEnabled,
        replyLayoutState = replyLayoutState,
        replyLayoutHeight = replyLayoutHeightClampedDp,
        replyLayoutVisibility = replyLayoutVisibility,
        onExpandReplyLayoutClicked = { replyLayoutState.expandReplyLayout() },
        onCollapseReplyLayoutClicked = { replyLayoutState.collapseReplyLayout() },
        onCancelReplySendClicked = { replyLayoutViewModel.cancelSendReply(replyLayoutState) },
        onSendReplyClicked = {
          if (chanDescriptor == null) {
            return@ReplyLayout
          }

          replyLayoutViewModel.sendReply(chanDescriptor, replyLayoutState)
        }
      )

      if (replyLayoutVisibility != ReplyLayoutVisibility.Closed) {
        Spacer(modifier = Modifier.height(windowInsets.bottom))
      }
    }
  }
}

@Composable
private fun ReplyLayout(
  replyLayoutEnabled: Boolean,
  replyLayoutState: ReplyLayoutState,
  replyLayoutHeight: Dp,
  replyLayoutVisibility: ReplyLayoutVisibility,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  if (replyLayoutHeight < 0.dp) {
    return
  }

  val replyText by replyLayoutState.replyText
  val sendReplyState by replyLayoutState.sendReplyState

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(replyLayoutHeight)
  ) {
    val density = LocalDensity.current
    val spacerHeight = 8.dp
    val replyInputHeightPercentage = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
      75f
    } else {
      65f
    }

    val replyLayoutHeightExcludingSpacer = replyLayoutHeight - spacerHeight
    val replyInputHeight = with(density) {
      ((replyLayoutHeightExcludingSpacer.toPx() / 100f) * replyInputHeightPercentage).toDp()
    }
    val replyAttachmentsHeight = replyLayoutHeightExcludingSpacer - replyInputHeight

    ReplyInputWithButtons(
      height = replyInputHeight,
      replyLayoutEnabled = replyLayoutEnabled,
      replyText = replyText,
      replyLayoutState = replyLayoutState,
      replyLayoutVisibility = replyLayoutVisibility,
      onExpandReplyLayoutClicked = onExpandReplyLayoutClicked,
      onCollapseReplyLayoutClicked = onCollapseReplyLayoutClicked,
      sendReplyState = sendReplyState,
      onCancelReplySendClicked = onCancelReplySendClicked,
      onSendReplyClicked = onSendReplyClicked
    )

    Spacer(modifier = Modifier.height(spacerHeight))

    ReplyAttachments(height = replyAttachmentsHeight)
  }
}

@Composable
fun ReplyAttachments(height: Dp) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
  ) {
    // TODO(KurobaEx):
    Text(text = "Attachments go here")
  }
}

@Composable
private fun ReplyInputWithButtons(
  height: Dp,
  replyLayoutEnabled: Boolean,
  replyText: String,
  replyLayoutState: ReplyLayoutState,
  replyLayoutVisibility: ReplyLayoutVisibility,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  sendReplyState: SendReplyState,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  val replySendProgressMut by replyLayoutState.replySendProgressState
  val replySendProgress = replySendProgressMut

  Row {
    KurobaComposeCustomTextField(
      modifier = Modifier
        .weight(1f)
        .height(height)
        .padding(top = 4.dp, start = 4.dp, end = 4.dp),
      enabled = replyLayoutEnabled,
      value = replyText,
      singleLine = false,
      maxLines = Int.MAX_VALUE,
      onValueChange = { newTextFieldValue -> replyLayoutState.onReplyTextChanged(newTextFieldValue) }
    )

    Column(
      modifier = Modifier
        .width(32.dp)
        .wrapContentHeight()
    ) {
      val drawableId = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
        R.drawable.ic_baseline_arrow_drop_down_24
      } else {
        R.drawable.ic_baseline_arrow_drop_up_24
      }

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .kurobaClickable(
            enabled = replyLayoutEnabled,
            bounded = false,
            onClick = {
              when (replyLayoutVisibility) {
                ReplyLayoutVisibility.Closed -> {
                  // no-op
                }
                ReplyLayoutVisibility.Opened -> onExpandReplyLayoutClicked()
                ReplyLayoutVisibility.Expanded -> onCollapseReplyLayoutClicked()
              }
            }
          ),
        drawableId = drawableId,
        enabled = replyLayoutEnabled
      )

      run {
        val buttonDrawableId = remember(key1 = sendReplyState) {
          if (sendReplyState.canCancel) {
            R.drawable.ic_baseline_close_24
          } else {
            R.drawable.ic_baseline_send_24
          }
        }

        Box(modifier = Modifier.size(32.dp)) {
          KurobaComposeIcon(
            modifier = Modifier
              .fillMaxSize()
              .padding(4.dp)
              .kurobaClickable(
                bounded = false,
                onClick = {
                  if (sendReplyState.canCancel) {
                    onCancelReplySendClicked()
                  } else {
                    onSendReplyClicked()
                  }
                }
              ),
            drawableId = buttonDrawableId
          )

          if (replySendProgress != null && replySendProgress > 0f) {
            KurobaComposeLoadingIndicator(
              modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
              progress = replySendProgress
            )
          }
        }
      }
    }
  }
}

private class ReplyLayoutTransitionState(
  val replyLayoutVisibility: ReplyLayoutVisibility
)
