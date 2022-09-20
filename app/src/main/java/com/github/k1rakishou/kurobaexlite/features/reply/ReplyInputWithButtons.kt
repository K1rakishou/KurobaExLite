package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowRow
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaLabelText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.LayoutOrientation
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SlotLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReplyInputWithButtons(
  replyInputHeight: Dp,
  replyLayoutState: ReplyLayoutState,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  val iconSize = 40.dp

  SlotLayout(
    modifier = Modifier
      .fillMaxWidth()
      .height(replyInputHeight),
    layoutOrientation = LayoutOrientation.Horizontal,
    builder = {
      dynamic(1f, "ReplyInput") {
        ReplyInput(
          replyLayoutState = replyLayoutState,
          onSendReplyClicked = onSendReplyClicked
        )
      }

      fixed(50.dp, "ReplyButtons") {
        ReplyButtons(
          iconSize = iconSize,
          replyLayoutState = replyLayoutState,
          onCancelReplySendClicked = onCancelReplySendClicked,
          onSendReplyClicked = onSendReplyClicked
        )
      }
    }
  )
}

@Composable
private fun ReplyInput(
  replyLayoutState: ReplyLayoutState,
  onSendReplyClicked: () -> Unit,
) {
  val focusManager = LocalFocusManager.current

  val sendReplyState by replyLayoutState.sendReplyState
  val replyLayoutVisibilityState by replyLayoutState.replyLayoutVisibilityState

  val replyLayoutEnabled = when (sendReplyState) {
    SendReplyState.Started,
    is SendReplyState.ReplySent -> false
    is SendReplyState.Finished -> true
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp),
  ) {
    if (replyLayoutVisibilityState == ReplyLayoutVisibility.Expanded) {
      if (replyLayoutState.chanDescriptor is CatalogDescriptor) {
        SubjectTextField(
          replyLayoutState = replyLayoutState,
          replyLayoutEnabled = replyLayoutEnabled,
          onMoveFocus = { focusManager.moveFocus(FocusDirection.Down) }
        )

        Spacer(modifier = Modifier.height(4.dp))
      }

      NameTextField(
        replyLayoutState = replyLayoutState,
        replyLayoutEnabled = replyLayoutEnabled,
        onMoveFocus = { focusManager.moveFocus(FocusDirection.Down) }
      )

      Spacer(modifier = Modifier.height(4.dp))

      OptionsTextField(
        replyLayoutState = replyLayoutState,
        replyLayoutEnabled = replyLayoutEnabled,
        onMoveFocus = { focusManager.moveFocus(FocusDirection.Down) }
      )

      Spacer(modifier = Modifier.height(4.dp))
    }

    ReplyInputTextField(
      replyLayoutState = replyLayoutState,
      replyLayoutEnabled = replyLayoutEnabled,
      onSendReplyClicked = onSendReplyClicked
    )

    Spacer(modifier = Modifier.height(4.dp))

    ReplyFormattingButtons(replyLayoutState = replyLayoutState)

    Spacer(modifier = Modifier.height(4.dp))
  }
}

@Composable
private fun ColumnScope.SubjectTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onMoveFocus: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val textStyle = remember { TextStyle(fontSize = 16.sp) }
  val interactionSource = remember { MutableInteractionSource() }
  val labelText = stringResource(id = R.string.reply_subject_label_text)

  val subject by replyLayoutState.subject

  KurobaComposeTextField(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    enabled = replyLayoutEnabled,
    value = subject,
    singleLine = true,
    textStyle = textStyle,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Sentences,
      imeAction = ImeAction.Next
    ),
    keyboardActions = KeyboardActions(onNext = { onMoveFocus() }),
    label = {
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        parentBackgroundColor = chanTheme.backColor,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onSubjectChanged(newTextFieldValue) },
    interactionSource = interactionSource
  )
}

@Composable
private fun ColumnScope.NameTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onMoveFocus: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val textStyle = remember { TextStyle(fontSize = 16.sp) }
  val interactionSource = remember { MutableInteractionSource() }
  val labelText = stringResource(id = R.string.reply_name_label_text)

  val name by replyLayoutState.name

  KurobaComposeTextField(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    enabled = replyLayoutEnabled,
    value = name,
    singleLine = true,
    textStyle = textStyle,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Sentences,
      imeAction = ImeAction.Next
    ),
    keyboardActions = KeyboardActions(onNext = { onMoveFocus() }),
    label = {
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        parentBackgroundColor = chanTheme.backColor,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onNameChanged(newTextFieldValue) },
    interactionSource = interactionSource
  )
}

@Composable
private fun ColumnScope.OptionsTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onMoveFocus: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val textStyle = remember { TextStyle(fontSize = 16.sp) }
  val interactionSource = remember { MutableInteractionSource() }
  val labelText = stringResource(id = R.string.reply_options_label_text)

  val options by replyLayoutState.options

  KurobaComposeTextField(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    enabled = replyLayoutEnabled,
    value = options,
    singleLine = true,
    textStyle = textStyle,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Sentences,
      imeAction = ImeAction.Next
    ),
    keyboardActions = KeyboardActions(onNext = { onMoveFocus() }),
    label = {
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        parentBackgroundColor = chanTheme.backColor,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onOptionsChanged(newTextFieldValue) },
    interactionSource = interactionSource
  )
}


@Composable
private fun ColumnScope.ReplyInputTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onSendReplyClicked: () -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val coroutineScope = rememberCoroutineScope()

  val focusRequest = remember { FocusRequester() }
  var prevReplyLayoutVisibility by remember { mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed) }

  val replyText by replyLayoutState.replyText
  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibilityState
  val maxCommentLength by replyLayoutState.maxCommentLength

  val replyInputVisualTransformation = remember(key1 = chanTheme, key2 = replyLayoutEnabled) {
    VisualTransformation { text ->
      val spannedText = buildAnnotatedString {
        append(text)

        if (text.text.isNotEmpty()) {
          val textColor = if (replyLayoutEnabled) chanTheme.textColorPrimary else chanTheme.textColorHint

          addStyle(
            style = SpanStyle(color = textColor),
            start = 0,
            end = text.length
          )
        }
      }

      return@VisualTransformation TransformedText(spannedText, OffsetMapping.Identity)
    }
  }

  DisposableEffect(
    key1 = replyLayoutVisibility,
    effect = {
      var job: Job? = null

      if (
        replyLayoutVisibility != ReplyLayoutVisibility.Collapsed &&
        prevReplyLayoutVisibility == ReplyLayoutVisibility.Collapsed
      ) {
        job?.cancel()
        job = coroutineScope.launch {
          delay(64)
          focusRequest.requestFocus()
        }
      } else if (
        replyLayoutVisibility == ReplyLayoutVisibility.Collapsed &&
        prevReplyLayoutVisibility != ReplyLayoutVisibility.Collapsed
      ) {
        job?.cancel()
        job = coroutineScope.launch {
          delay(64)
          focusRequest.freeFocus()
        }
      }

      prevReplyLayoutVisibility = replyLayoutVisibility

      onDispose {
        job?.cancel()
        focusRequest.freeFocus()
      }
    }
  )

  val newThreadText = stringResource(id = R.string.reply_input_new_thread_label_text)
  val postCommentText = stringResource(id = R.string.reply_input_thread_comment_label_text)

  Box(modifier = Modifier.weight(1f)) {
    val textStyle = remember { TextStyle(fontSize = 16.sp) }
    val interactionSource = remember { MutableInteractionSource() }

    val labelText = remember(replyLayoutState.chanDescriptor, replyText.text.length) {
      buildString {
        val commentLabelText = when (replyLayoutState.chanDescriptor) {
          is CatalogDescriptor -> newThreadText
          is ThreadDescriptor -> postCommentText
        }

        append(commentLabelText)

        if (replyText.text.isNotEmpty()) {
          append(" ")
          append("(")

          append(replyText.text.length)

          if (maxCommentLength > 0) {
            append("/")
            append(maxCommentLength)
          }

          append(")")
        }
      }
    }

    KurobaComposeTextField(
      modifier = Modifier
        .fillMaxSize()
        .padding(vertical = 4.dp)
        .focusRequester(focusRequest),
      enabled = replyLayoutEnabled,
      value = replyText,
      singleLine = false,
      maxLines = Int.MAX_VALUE,
      textStyle = textStyle,
      visualTransformation = replyInputVisualTransformation,
      keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done
      ),
      keyboardActions = KeyboardActions(onDone = { onSendReplyClicked() }),
      label = {
        KurobaLabelText(
          enabled = replyLayoutEnabled,
          labelText = labelText,
          fontSize = 14.sp,
          parentBackgroundColor = chanTheme.backColor,
          interactionSource = interactionSource
        )
      },
      onValueChange = { newTextFieldValue -> replyLayoutState.onReplyTextChanged(newTextFieldValue) },
      interactionSource = interactionSource
    )
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
  iconSize: Dp,
  replyLayoutState: ReplyLayoutState,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  val replySendProgressMut by replyLayoutState.replySendProgressState
  val sendReplyState by replyLayoutState.sendReplyState

  val replySendProgress = replySendProgressMut

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    SendReplyButton(
      sendReplyState = sendReplyState,
      iconSize = iconSize,
      onCancelReplySendClicked = onCancelReplySendClicked,
      onSendReplyClicked = onSendReplyClicked,
      replySendProgress = replySendProgress
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