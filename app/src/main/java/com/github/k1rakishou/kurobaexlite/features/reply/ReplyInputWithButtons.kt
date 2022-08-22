package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.LayoutOrientation
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SlotLayout

@Composable
fun ReplyInputWithButtons(
  height: Dp,
  replyLayoutState: ReplyLayoutState,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val iconSize = 40.dp

  val replySendProgressMut by replyLayoutState.replySendProgressState
  val replyText by replyLayoutState.replyText
  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibilityState
  val sendReplyState by replyLayoutState.sendReplyState

  val replySendProgress = replySendProgressMut

  val replyLayoutEnabled = when (sendReplyState) {
    SendReplyState.Started,
    is SendReplyState.ReplySent -> false
    is SendReplyState.Finished -> true
  }

  val replyInputVisualTransformation = remember {
    VisualTransformation { text ->
      val spannedText = buildAnnotatedString {
        append(text)

        if (text.text.isNotEmpty()) {
          addStyle(
            style = SpanStyle(color = chanTheme.textColorPrimary),
            start = 0,
            end = text.length
          )
        }
      }

      return@VisualTransformation TransformedText(spannedText, OffsetMapping.Identity)
    }
  }

  SlotLayout(
    modifier = Modifier
      .fillMaxWidth()
      .height(height),
    layoutOrientation = LayoutOrientation.Horizontal,
    builder = {
      dynamic(1f, "ReplyTextField") {
        ReplyTextField(
          replyLayoutEnabled = replyLayoutEnabled,
          replyText = replyText,
          replyInputVisualTransformation = replyInputVisualTransformation,
          replyLayoutState = replyLayoutState
        )
      }

      fixed(50.dp, "ReplyButtons") {
        ReplyButtons(
          replyLayoutVisibility = replyLayoutVisibility,
          iconSize = iconSize,
          replyLayoutEnabled = replyLayoutEnabled,
          onExpandReplyLayoutClicked = onExpandReplyLayoutClicked,
          onCollapseReplyLayoutClicked = onCollapseReplyLayoutClicked,
          sendReplyState = sendReplyState,
          onCancelReplySendClicked = onCancelReplySendClicked,
          onSendReplyClicked = onSendReplyClicked,
          replySendProgress = replySendProgress
        )
      }
    }
  )
}

@Composable
private fun ReplyTextField(
  replyLayoutEnabled: Boolean,
  replyText: TextFieldValue,
  replyInputVisualTransformation: VisualTransformation,
  replyLayoutState: ReplyLayoutState
) {
  KurobaComposeCustomTextField(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = 4.dp, horizontal = 8.dp),
    enabled = replyLayoutEnabled,
    value = replyText,
    singleLine = false,
    maxLines = Int.MAX_VALUE,
    visualTransformation = replyInputVisualTransformation,
    onValueChange = { newTextFieldValue ->
      replyLayoutState.onReplyTextChanged(newTextFieldValue)
    }
  )
}

@Composable
private fun ReplyButtons(
  replyLayoutVisibility: ReplyLayoutVisibility,
  iconSize: Dp,
  replyLayoutEnabled: Boolean,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  sendReplyState: SendReplyState,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  replySendProgress: Float?
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Top,
  ) {
    run {
      val drawableId = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
        R.drawable.ic_baseline_arrow_drop_down_24
      } else {
        R.drawable.ic_baseline_arrow_drop_up_24
      }

      val iconScale = remember { FixedScale(2f) }

      Box(modifier = Modifier.size(iconSize)) {
        KurobaComposeIcon(
          modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
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
          contentScale = iconScale,
          enabled = replyLayoutEnabled
        )
      }
    }

    run {
      val buttonDrawableId = remember(key1 = sendReplyState) {
        if (sendReplyState.canCancel) {
          R.drawable.ic_baseline_close_24
        } else {
          R.drawable.ic_baseline_send_24
        }
      }

      Box(modifier = Modifier.size(iconSize)) {
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