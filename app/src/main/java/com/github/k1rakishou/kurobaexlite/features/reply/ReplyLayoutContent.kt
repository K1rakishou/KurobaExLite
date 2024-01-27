package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.focusable
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
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastSumBy
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.parser.AbstractSitePostParser
import com.github.k1rakishou.kurobaexlite.helpers.util.ensureSingleMeasurable
import com.github.k1rakishou.kurobaexlite.helpers.util.freeFocusSafe
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowRow
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaLabelText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.verticalScrollbar
import com.github.k1rakishou.kurobaexlite.ui.themes.ChanTheme
import java.util.regex.Pattern

private val InlineQuoteRegex by lazy { Pattern.compile("^(>.*)", Pattern.MULTILINE) }
private val QuoteRegex by lazy { Pattern.compile("(>>\\d+)") }

@Composable
fun ReplyLayoutContent(
  replyLayoutState: ReplyLayoutState,
  draggableStateProvider: () -> DraggableState,
  onDragStarted: suspend () -> Unit,
  onDragStopped: suspend (velocity: Float) -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
  val iconSize = 40.dp
  val replyButtonsWidth = 58.dp

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
        onAttachedMediaClicked = onAttachedMediaClicked,
        onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
        onFlagSelectorClicked = onFlagSelectorClicked
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
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
  val focusManager = LocalFocusManager.current
  val replyLayoutViewModel = koinRememberViewModel<ReplyLayoutViewModel>()

  val sendReplyState by replyLayoutState.sendReplyState
  val replyLayoutVisibilityState by replyLayoutState.replyLayoutVisibilityState
  val attachedMediaList = replyLayoutState.attachedMediaList

  val replyLayoutEnabled = when (sendReplyState) {
    SendReplyState.Started,
    is SendReplyState.ReplySent -> false
    is SendReplyState.Finished -> true
  }

  LaunchedEffect(
    key1 = replyLayoutState.chanDescriptor,
    block = { replyLayoutViewModel.loadLastUsedFlag(replyLayoutState.chanDescriptor) }
  )

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

          FlagSelector(
            replyLayoutState = replyLayoutState,
            replyLayoutEnabled = replyLayoutEnabled,
            onFlagSelectorClicked = onFlagSelectorClicked
          )

          Spacer(modifier = Modifier.height(4.dp))
        }
      }
    },
    replyInputContent = {
      Column {
        ReplyTextField(
          replyLayoutState = replyLayoutState,
          replyLayoutEnabled = replyLayoutEnabled
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
  Layout(
    contents = listOf(additionalInputsContent, replyInputContent, formattingButtonsContent, replyAttachmentsContent),
    modifier = modifier,
    measurePolicy = { measurables, constraints ->
      val additionalInputsContentMeasurables = measurables[0]
      val replyInputContentMeasurable = measurables[1].ensureSingleMeasurable()
      val formattingButtonsContentMeasurable = measurables[2].ensureSingleMeasurable()
      val replyAttachmentsContentMeasurables = measurables[3]

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

      return@Layout layout(totalWidth, totalHeight) {
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
  val textStyle = remember { TextStyle(fontSize = 16.sp) }
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
    label = { interactionSource ->
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onSubjectChanged(newTextFieldValue) }
  )
}

@Composable
private fun ColumnScope.NameTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onMoveFocus: () -> Unit
) {
  val textStyle = remember { TextStyle(fontSize = 16.sp) }
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
    label = { interactionSource ->
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onNameChanged(newTextFieldValue) }
  )
}

@Composable
private fun ColumnScope.FlagSelector(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val loadChanCatalog = koinRemember<LoadChanCatalog>()

  var flags by remember { mutableStateOf<List<BoardFlag>>(emptyList()) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      flags = loadChanCatalog.await(replyLayoutState.chanDescriptor).getOrNull()
        ?.flags
        ?: emptyList()
    }
  )

  if (flags.isEmpty()) {
    return
  }

  var lastUsedFlagMut by remember { mutableStateOf<BoardFlag?>(null) }
  val lastUsedFlag = lastUsedFlagMut

  LaunchedEffect(
    key1 = Unit,
    block = {
      lastUsedFlagMut = replyLayoutState.flag.value

      snapshotFlow { replyLayoutState.flag.value }
        .collect { newSelectedFlag -> lastUsedFlagMut = newSelectedFlag }
    }
  )

  if (lastUsedFlag == null) {
    return
  }

  val lastUsedFlagText = remember(key1 = lastUsedFlag) { lastUsedFlag.asUserReadableString() }
  val flagSelectorAlpha = if (replyLayoutEnabled) 1f else ContentAlpha.disabled

  Spacer(modifier = Modifier.height(16.dp))

  KurobaComposeText(
    modifier = Modifier.padding(start = 6.dp),
    color = chanTheme.textColorHint,
    text = stringResource(id = R.string.reply_flag_label_text)
  )

  Spacer(modifier = Modifier.height(8.dp))

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(42.dp)
      .drawBehind {
        drawRoundRect(
          color = chanTheme.backColorSecondary,
          topLeft = Offset.Zero,
          size = Size(
            width = this.size.width,
            height = this.size.height
          ),
          alpha = 0.4f,
          cornerRadius = CornerRadius(4.dp.toPx())
        )
      }
      .kurobaClickable(
        enabled = replyLayoutEnabled,
        bounded = true,
        onClick = { onFlagSelectorClicked(replyLayoutState.chanDescriptor) }
      )
      .padding(horizontal = 8.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    KurobaComposeText(
      modifier = Modifier
        .weight(1f)
        .wrapContentHeight()
        .graphicsLayer { alpha = flagSelectorAlpha },
      text = lastUsedFlagText,
      fontSize = 16.sp
    )

    Spacer(modifier = Modifier.width(8.dp))

    KurobaComposeIcon(drawableId = R.drawable.ic_baseline_arrow_drop_down_24)

    Spacer(modifier = Modifier.width(8.dp))
  }

  Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ColumnScope.OptionsTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean,
  onMoveFocus: () -> Unit
) {
  val textStyle = remember { TextStyle(fontSize = 16.sp) }
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
    label = { interactionSource ->
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onOptionsChanged(newTextFieldValue) }
  )
}


@Composable
private fun ReplyTextField(
  replyLayoutState: ReplyLayoutState,
  replyLayoutEnabled: Boolean
) {
  val chanTheme = LocalChanTheme.current
  val localSoftwareKeyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  var prevReplyLayoutVisibility by remember { mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed) }
  val replyText by replyLayoutState.replyText
  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibilityState
  val maxCommentLength by replyLayoutState.maxCommentLength

  val disabledAlpha = ContentAlpha.disabled

  val replyInputVisualTransformation = remember(key1 = chanTheme, key2 = replyLayoutEnabled, key3 = disabledAlpha) {
    VisualTransformation { text ->
      val spannedText = colorizeReplyInputText(
        disabledAlpha = disabledAlpha,
        text = text,
        replyLayoutEnabled = replyLayoutEnabled,
        chanTheme = chanTheme
      )

      return@VisualTransformation TransformedText(spannedText, OffsetMapping.Identity)
    }
  }

  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(
    key1 = replyLayoutVisibility,
    block = {
      if (
        prevReplyLayoutVisibility == ReplyLayoutVisibility.Collapsed &&
        replyLayoutVisibility == ReplyLayoutVisibility.Opened
      ) {
        focusRequester.freeFocusSafe()
        localSoftwareKeyboardController?.show()
      } else if (
        prevReplyLayoutVisibility != ReplyLayoutVisibility.Collapsed &&
        replyLayoutVisibility == ReplyLayoutVisibility.Collapsed
      ) {
        focusRequester.freeFocusSafe()

        if (!globalUiInfoManager.isAnyReplyLayoutOpened()) {
          localSoftwareKeyboardController?.hide()
        }
      } else if (
        prevReplyLayoutVisibility != ReplyLayoutVisibility.Expanded &&
        replyLayoutVisibility == ReplyLayoutVisibility.Expanded
      ) {
        focusRequester.freeFocusSafe()
        focusManager.clearFocus()
      }

      prevReplyLayoutVisibility = replyLayoutVisibility
    }
  )

  DisposableEffect(
    key1 = Unit,
    effect = {
      onDispose {
        focusRequester.freeFocusSafe()

        if (!globalUiInfoManager.isAnyReplyLayoutOpened()) {
          localSoftwareKeyboardController?.hide()
        }
      }
    }
  )

  val newThreadText = stringResource(id = R.string.reply_input_new_thread_label_text)
  val postCommentText = stringResource(id = R.string.reply_input_thread_comment_label_text)

  val textStyle = remember { TextStyle(fontSize = 16.sp) }

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

  val mutableInteractionSource = remember { MutableInteractionSource() }

  KurobaComposeTextField(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = 4.dp)
      .focusable(interactionSource = mutableInteractionSource)
      .focusRequester(focusRequester)
      .then(minHeightModifier),
    enabled = replyLayoutEnabled,
    value = replyText,
    singleLine = false,
    maxLines = Int.MAX_VALUE,
    textStyle = textStyle,
    visualTransformation = replyInputVisualTransformation,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Sentences
    ),
    label = { interactionSource ->
      KurobaLabelText(
        enabled = replyLayoutEnabled,
        labelText = labelText,
        fontSize = 14.sp,
        interactionSource = interactionSource
      )
    },
    onValueChange = { newTextFieldValue -> replyLayoutState.onReplyTextChanged(newTextFieldValue) },
    interactionSource = mutableInteractionSource
  )
}

private fun colorizeReplyInputText(
  disabledAlpha: Float,
  text: AnnotatedString,
  replyLayoutEnabled: Boolean,
  chanTheme: ChanTheme
): AnnotatedString {
  return buildAnnotatedString {
    append(text)

    val textAlpha = if (replyLayoutEnabled) 1f else disabledAlpha
    val textColor = chanTheme.textColorPrimary.copy(alpha = textAlpha)
    val postInlineQuoteColor = chanTheme.postInlineQuoteColor.copy(alpha = textAlpha)
    val quoteColor = chanTheme.postQuoteColor.copy(alpha = textAlpha)
    val postLinkColor = chanTheme.postLinkColor.copy(alpha = textAlpha)

    addStyle(
      style = SpanStyle(color = textColor),
      start = 0,
      end = text.length
    )

    val inlineQuoteMatcher = InlineQuoteRegex.matcher(text)
    while (inlineQuoteMatcher.find()) {
      val start = inlineQuoteMatcher.start(1)
      val end = inlineQuoteMatcher.end(1)

      if (start >= end) {
        continue
      }

      addStyle(
        style = SpanStyle(color = postInlineQuoteColor),
        start = start,
        end = end
      )
    }

    val quoteMatcher = QuoteRegex.matcher(text)
    while (quoteMatcher.find()) {
      val start = quoteMatcher.start(1)
      val end = quoteMatcher.end(1)

      if (start >= end) {
        continue
      }

      addStyle(
        style = SpanStyle(
          color = quoteColor,
          textDecoration = TextDecoration.Underline
        ),
        start = start,
        end = end
      )
    }

    AbstractSitePostParser.LINK_EXTRACTOR.extractLinks(text).forEach { linkSpan ->
      addStyle(
        style = SpanStyle(
          color = postLinkColor,
          textDecoration = TextDecoration.Underline
        ),
        start = linkSpan.beginIndex,
        end = linkSpan.endIndex
      )
    }
  }
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
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val replySendProgressMut by replyLayoutState.replySendProgressState
  val replySendProgress = replySendProgressMut
  val sendReplyState by replyLayoutState.sendReplyState

  val padding = with(density) { 8.dp.toPx() }
  val cornerRadius = with(density) { remember { CornerRadius(8.dp.toPx(), 8.dp.toPx()) } }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .draggable(
        state = draggableStateProvider(),
        orientation = Orientation.Vertical,
        onDragStarted = { onDragStarted() },
        onDragStopped = { velocity -> onDragStopped(velocity) }
      )
      .drawBehind {
        drawRoundRect(
          color = chanTheme.backColorSecondary,
          topLeft = Offset(x = padding, y = padding),
          size = Size(
            width = this.size.width - (padding * 2),
            height = this.size.height - (padding * 2)
          ),
          alpha = 0.4f,
          cornerRadius = cornerRadius
        )
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(8.dp))

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