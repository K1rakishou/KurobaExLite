package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowRow
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.LayoutOrientation
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SlotLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReplyInputWithButtons(
  height: Dp,
  replyLayoutState: ReplyLayoutState,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  onCloseReplyLayoutClicked: () -> Unit,
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
      dynamic(1f, "ReplyInput") {
        ReplyInput(
          replyLayoutEnabled = replyLayoutEnabled,
          replyText = replyText,
          replyLayoutVisibility = replyLayoutVisibility,
          replyLayoutState = replyLayoutState,
          replyInputVisualTransformation = replyInputVisualTransformation,
          onSendReplyClicked = onSendReplyClicked
        )
      }

      fixed(50.dp, "ReplyButtons") {
        ReplyButtons(
          replyLayoutVisibility = replyLayoutVisibility,
          iconSize = iconSize,
          replyLayoutEnabled = replyLayoutEnabled,
          onExpandReplyLayoutClicked = onExpandReplyLayoutClicked,
          onCollapseReplyLayoutClicked = onCollapseReplyLayoutClicked,
          onCloseReplyLayoutClicked = onCloseReplyLayoutClicked,
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
private fun ReplyInput(
  replyLayoutEnabled: Boolean,
  replyText: TextFieldValue,
  replyLayoutVisibility: ReplyLayoutVisibility,
  replyLayoutState: ReplyLayoutState,
  replyInputVisualTransformation: VisualTransformation,
  onSendReplyClicked: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp),
  ) {
    val focusRequest = remember { FocusRequester() }
    var prevReplyLayoutVisibility by remember { mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Closed) }

    DisposableEffect(
      key1 = replyLayoutVisibility,
      effect = {
        var job: Job? = null

        if (
          replyLayoutVisibility != ReplyLayoutVisibility.Closed &&
          prevReplyLayoutVisibility == ReplyLayoutVisibility.Closed
        ) {
          job = coroutineScope.launch {
            delay(64)
            focusRequest.requestFocus()
          }
        }

        prevReplyLayoutVisibility = replyLayoutVisibility

        onDispose {
          job?.cancel()
          focusRequest.freeFocus()
        }
      }
    )

    KurobaComposeCustomTextField(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(vertical = 4.dp)
        .focusable()
        .focusRequester(focusRequest),
      enabled = replyLayoutEnabled,
      value = replyText,
      singleLine = false,
      maxLines = Int.MAX_VALUE,
      visualTransformation = replyInputVisualTransformation,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      keyboardActions = KeyboardActions(onDone = { onSendReplyClicked() }),
      onValueChange = { newTextFieldValue ->
        replyLayoutState.onReplyTextChanged(newTextFieldValue)
      }
    )

    Spacer(modifier = Modifier.height(4.dp))

    ReplyFormattingButtons(replyLayoutState = replyLayoutState)

    Spacer(modifier = Modifier.height(4.dp))
  }
}

@Composable
private fun ReplyFormattingButtons(replyLayoutState: ReplyLayoutState) {
  val replyFormattingButtons by replyLayoutState.replyFormattingButtons.collectAsState()
  if (replyFormattingButtons.isEmpty()) {
    return
  }

  FlowRow(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
  ) {
    replyFormattingButtons.forEach { formattingButton ->
      key(formattingButton.openTag) {
        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentWidth()
            .widthIn(min = 42.dp)
            .height(38.dp),
          text = formattingButton.title,
          fontSize = 16.sp,
          onClick = { replyLayoutState.insertTags(formattingButton) }
        )
      }
    }
  }
}

@Composable
private fun ReplyButtons(
  replyLayoutVisibility: ReplyLayoutVisibility,
  iconSize: Dp,
  replyLayoutEnabled: Boolean,
  sendReplyState: SendReplyState,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  onCloseReplyLayoutClicked: () -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  replySendProgress: Float?
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ExpandCollapseButton(
      replyLayoutVisibility = replyLayoutVisibility,
      iconSize = iconSize,
      replyLayoutEnabled = replyLayoutEnabled,
      onExpandReplyLayoutClicked = onExpandReplyLayoutClicked,
      onCollapseReplyLayoutClicked = onCollapseReplyLayoutClicked
    )

    SendReplyButton(
      sendReplyState = sendReplyState,
      iconSize = iconSize,
      onCancelReplySendClicked = onCancelReplySendClicked,
      onSendReplyClicked = onSendReplyClicked,
      replySendProgress = replySendProgress
    )

    Spacer(modifier = Modifier.weight(1f))

    CloseReplyLayoutButton(
      iconSize = iconSize,
      onCloseReplyLayoutClicked = onCloseReplyLayoutClicked
    )
  }
}

@Composable
private fun ExpandCollapseButton(
  replyLayoutVisibility: ReplyLayoutVisibility,
  iconSize: Dp,
  replyLayoutEnabled: Boolean,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit
) {
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

@Composable
private fun SendReplyButton(
  sendReplyState: SendReplyState,
  iconSize: Dp,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  replySendProgress: Float?
) {
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

@Composable
private fun CloseReplyLayoutButton(
  iconSize: Dp,
  onCloseReplyLayoutClicked: () -> Unit
) {
  val androidHelpers = koinRemember<AndroidHelpers>()
  val view = LocalView.current
  val navigationGesturesUsed = remember { androidHelpers.navigationGesturesUsed(view) }

  // Just press the back button on the navigation bar to close it.
  if (!navigationGesturesUsed) {
    return
  }

  val iconScale = remember { FixedScale(2f) }

  Box(modifier = Modifier.size(iconSize)) {
    KurobaComposeIcon(
      modifier = Modifier
        .fillMaxSize()
        .padding(4.dp)
        .graphicsLayer {
          rotationZ = 90f
        }
        .kurobaClickable(
          bounded = false,
          onClick = onCloseReplyLayoutClicked
        ),
      drawableId = R.drawable.ic_baseline_chevron_right_24,
      contentScale = iconScale,
    )
  }
}