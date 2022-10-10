package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
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
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastSumBy
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.ensureSingleMeasurable
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowRow
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaLabelText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.verticalScrollbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReplyLayoutContent(
  replyLayoutState: ReplyLayoutState,
  draggableStateProvider: () -> DraggableState,
  onDragStarted: suspend () -> Unit,
  onDragStopped: suspend (velocity: Float) -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  val iconSize = 40.dp
  val replyButtonsWidth = 50.dp

  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibilityState

  val scrollState = rememberScrollState()
  val emptyPaddings = remember { PaddingValues() }

  val scrollModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier
      .verticalScroll(state = scrollState)
      .verticalScrollbar(contentPadding = emptyPaddings, scrollState = scrollState)
  } else {
    Modifier
  }

  val heightModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier.wrapContentHeight()
  } else {
    Modifier.fillMaxHeight()
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(heightModifier),
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .then(scrollModifier)
    ) {
      ReplyInputLeftPart(
        replyLayoutState = replyLayoutState,
        onSendReplyClicked = onSendReplyClicked,
        onAttachedMediaClicked = onAttachedMediaClicked,
        onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
      )
    }

    Box(
      modifier = Modifier
        .width(replyButtonsWidth)
        .fillMaxHeight()
    ) {
      ReplyInputRightPart(
        iconSize = iconSize,
        replyLayoutState = replyLayoutState,
        draggableStateProvider = draggableStateProvider,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
        onCancelReplySendClicked = onCancelReplySendClicked,
        onSendReplyClicked = onSendReplyClicked
      )
    }
  }
}

@Composable
private fun ReplyInputLeftPart(
  replyLayoutState: ReplyLayoutState,
  onSendReplyClicked: () -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  val focusManager = LocalFocusManager.current

  val sendReplyState by replyLayoutState.sendReplyState
  val replyLayoutVisibilityState by replyLayoutState.replyLayoutVisibilityState
  val attachedMediaList = replyLayoutState.attachedMediaList

  val replyLayoutEnabled = when (sendReplyState) {
    SendReplyState.Started,
    is SendReplyState.ReplySent -> false
    is SendReplyState.Finished -> true
  }

  ReplyLayoutLeftPartCustomLayout(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp),
    additionalInputsContent = {
      if (replyLayoutVisibilityState == ReplyLayoutVisibility.Expanded) {
        Column {
          if (replyLayoutState.isCatalogMode) {
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
      }
    },
    replyInputContent = {
      Column {
        ReplyTextField(
          replyLayoutState = replyLayoutState,
          replyLayoutEnabled = replyLayoutEnabled,
          onSendReplyClicked = onSendReplyClicked
        )

        Spacer(modifier = Modifier.height(4.dp))
      }
    },
    formattingButtonsContent = {
      Column {
        ReplyFormattingButtons(replyLayoutState = replyLayoutState)

        Spacer(modifier = Modifier.height(4.dp))
      }
    },
    replyAttachmentsContent = {
      if (attachedMediaList.isNotEmpty()) {
        ReplyAttachments(
          replyLayoutState = replyLayoutState,
          onAttachedMediaClicked = onAttachedMediaClicked,
          onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
        )
      }
    }
  )
}

@Composable
private fun ReplyLayoutLeftPartCustomLayout(
  modifier: Modifier,
  additionalInputsContent: @Composable () -> Unit,
  replyInputContent: @Composable () -> Unit,
  formattingButtonsContent: @Composable () -> Unit,
  replyAttachmentsContent: @Composable () -> Unit
) {
  SubcomposeLayout(
    modifier = modifier,
    measurePolicy = { constraints ->
      val additionalInputsContentMeasurables = subcompose("additionalInputsContent", additionalInputsContent)
      val replyInputContentMeasurable = subcompose("replyInputContent", replyInputContent)
        .ensureSingleMeasurable()
      val formattingButtonsContentMeasurable = subcompose("formattingButtonsContent", formattingButtonsContent)
        .ensureSingleMeasurable()
      val replyAttachmentsContentMeasurables = subcompose("replyAttachmentsContent", replyAttachmentsContent)

      val placeables = mutableListWithCap<Placeable>(
        additionalInputsContentMeasurables.size +
          replyAttachmentsContentMeasurables.size +
          2 // replyInputContentMeasurable + formattingButtonsContentMeasurable
      )

      if (constraints.hasBoundedHeight) {
        var remainingHeight = constraints.maxHeight

        placeables += additionalInputsContentMeasurables
          .map { it.measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight)) }
          .also { newPlaceables -> remainingHeight -= newPlaceables.fastSumBy { it.measuredHeight } }

        val formattingButtonsContentPlaceable = formattingButtonsContentMeasurable
          .measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight))
          .also { newPlaceable -> remainingHeight -= newPlaceable.measuredHeight }

        val replyAttachmentsContentPlaceables = replyAttachmentsContentMeasurables
          .map { it.measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight)) }
          .also { newPlaceables -> remainingHeight -= newPlaceables.fastSumBy { it.measuredHeight } }

        placeables += replyInputContentMeasurable
          .measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight))
          .also { newPlaceable -> remainingHeight -= newPlaceable.measuredHeight }

        placeables += formattingButtonsContentPlaceable
        placeables += replyAttachmentsContentPlaceables
      } else {
        // We are inside scrolling container, just stack everything up vertically we don't care about vertical space
        placeables += additionalInputsContentMeasurables.map { it.measure(constraints) }
        placeables += replyInputContentMeasurable.measure(constraints)
        placeables += formattingButtonsContentMeasurable.measure(constraints)
        placeables += replyAttachmentsContentMeasurables.map { it.measure(constraints) }
      }

      val totalWidth = placeables.maxBy { it.measuredHeight }.width
      val totalHeight = placeables.fold(0) { acc, placeable -> acc + placeable.measuredHeight }

      return@SubcomposeLayout layout(totalWidth, totalHeight) {
        var yOffset = 0

        placeables.fastForEach { placeable ->
          placeable.place(0, yOffset)
          yOffset += placeable.measuredHeight
        }
      }
    }
  )
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
private fun ColumnScope.ReplyTextField(
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

  val textStyle = remember { TextStyle(fontSize = 16.sp) }
  val interactionSource = remember { MutableInteractionSource() }

  val labelText = remember(replyLayoutState.isCatalogMode, replyText.text.length) {
    buildString {
      val commentLabelText = if (replyLayoutState.isCatalogMode) {
        newThreadText
      } else {
        postCommentText
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

  val minHeightModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier.heightIn(min = 200.dp)
  } else {
    Modifier
  }

  KurobaComposeTextField(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = 4.dp)
      .focusRequester(focusRequest)
      .then(minHeightModifier),
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

@Composable
private fun ColumnScope.ReplyFormattingButtons(replyLayoutState: ReplyLayoutState) {
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
private fun ReplyInputRightPart(
  iconSize: Dp,
  replyLayoutState: ReplyLayoutState,
  draggableStateProvider: () -> DraggableState,
  onDragStarted: suspend () -> Unit,
  onDragStopped: suspend (velocity: Float) -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  val replySendProgressMut by replyLayoutState.replySendProgressState
  val sendReplyState by replyLayoutState.sendReplyState

  val replySendProgress = replySendProgressMut

  Column(
    modifier = Modifier
      .fillMaxSize()
      .draggable(
        state = draggableStateProvider(),
        orientation = Orientation.Vertical,
        onDragStarted = { onDragStarted() },
        onDragStopped = { velocity -> onDragStopped(velocity) }
      ),
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

  val padding = if (sendReplyState.canCancel) {
    10.dp
  } else {
    4.dp
  }

  Box(modifier = Modifier.size(iconSize)) {
    KurobaComposeIcon(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
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

    if (replySendProgress != null) {
      if (replySendProgress > 0f && replySendProgress < 1f) {
        KurobaComposeLoadingIndicator(
          modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
          progress = replySendProgress
        )
      } else if (replySendProgress >= 1f) {
        KurobaComposeLoadingIndicator(
          modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        )
      }
    }
  }
}