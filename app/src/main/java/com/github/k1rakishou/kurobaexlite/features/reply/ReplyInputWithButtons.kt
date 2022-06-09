package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

@Composable
fun ReplyInputWithButtons(
  height: Dp,
  replyLayoutEnabled: Boolean,
  replyText: TextFieldValue,
  replyLayoutState: ReplyLayoutState,
  replyLayoutVisibility: ReplyLayoutVisibility,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  sendReplyState: SendReplyState,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val replySendProgressMut by replyLayoutState.replySendProgressState
  val replySendProgress = replySendProgressMut

  val iconSize = 40.dp

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
      visualTransformation = replyInputVisualTransformation,
      onValueChange = { newTextFieldValue ->
        replyLayoutState.onReplyTextChanged(newTextFieldValue)
      }
    )

    Column(
      modifier = Modifier
        .wrapContentSize()
    ) {
      val drawableId = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
        R.drawable.ic_baseline_arrow_drop_down_24
      } else {
        R.drawable.ic_baseline_arrow_drop_up_24
      }

      KurobaComposeIcon(
        modifier = Modifier
          .size(iconSize)
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
}