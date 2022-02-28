package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.annotation.DrawableRes
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.detectTapGesturesWithFilter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import java.util.*

private val DefaultFillMaxSizeModifier: Modifier = Modifier.fillMaxSize()
private val defaultNoopClickCallback = { }

@Composable
fun KurobaComposeLoadingIndicator(
  modifier: Modifier = DefaultFillMaxSizeModifier,
  overrideColor: Color? = null,
  indicatorSize: Dp = 42.dp
) {
  Box(modifier = modifier) {
    val color = if (overrideColor == null) {
      val chanTheme = LocalChanTheme.current
      remember(key1 = chanTheme.accentColor) { Color(chanTheme.accentColor) }
    } else {
      overrideColor
    }

    CircularProgressIndicator(
      color = color,
      modifier = Modifier
        .align(Alignment.Center)
        .size(indicatorSize, indicatorSize)
    )
  }
}

@Composable
fun KurobaComposeErrorWithButton(
  modifier: Modifier = DefaultFillMaxSizeModifier,
  errorMessage: String,
  buttonText: String,
  onButtonClicked: () -> Unit
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val chanTheme = LocalChanTheme.current

    KurobaComposeText(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth(),
      text = errorMessage
    )

    Spacer(modifier = Modifier.height(12.dp))

    KurobaComposeTextButton(
      text = buttonText,
      onClick = onButtonClicked
    )
  }
}


@Composable
fun KurobaComposeText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: Map<String, InlineTextContent> = mapOf()
) {
  KurobaComposeText(
    text = AnnotatedString(text),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    enabled = enabled,
    textAlign = textAlign,
    inlineContent = inlineContent
  )
}

@Composable
fun KurobaComposeText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: Map<String, InlineTextContent> = mapOf(),
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val textColorPrimary = if (color == null) {
    val chanTheme = LocalChanTheme.current

    remember(key1 = chanTheme.textColorPrimary) {
      Color(chanTheme.textColorPrimary)
    }
  } else {
    color
  }

  val actualTextColorPrimary = if (enabled) {
    textColorPrimary
  } else {
    textColorPrimary.copy(alpha = ContentAlpha.disabled)
  }

  Text(
    modifier = modifier,
    color = actualTextColorPrimary,
    text = text,
    fontSize = fontSize,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    inlineContent = inlineContent,
    onTextLayout = onTextLayout
  )
}

@Composable
fun KurobaComposeClickableText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: Map<String, InlineTextContent> = mapOf(),
  annotationBgColors: Map<String, Color> = mapOf(),
  detectClickedAnnotations: (Offset, TextLayoutResult?, AnnotatedString) -> AnnotatedString.Range<String>?,
  onTextAnnotationClicked: (AnnotatedString, Int) -> Unit
) {
  var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  var currentlyPressedAnnotationPath by remember { mutableStateOf<Path?>(null) }
  var currentPressedAnnotationBgColor by remember { mutableStateOf<Color?>(null) }

  val pressIndicator = Modifier.pointerInput(key1 = text) {
    detectTapGesturesWithFilter(
      processDownEvent = { pos ->
        val layoutRes = layoutResult
          ?: return@detectTapGesturesWithFilter false
        val clickedAnnotation = detectClickedAnnotations(pos, layoutRes, text)
          ?: return@detectTapGesturesWithFilter false

        val path = layoutRes.getPathForRange(clickedAnnotation.start, clickedAnnotation.end)
        if (path.isEmpty) {
          return@detectTapGesturesWithFilter false
        }

        currentPressedAnnotationBgColor = annotationBgColors[clickedAnnotation.tag]
        currentlyPressedAnnotationPath = path
        return@detectTapGesturesWithFilter true
      },
      onTap = { pos ->
        currentlyPressedAnnotationPath = null

        layoutResult?.let { result ->
          val offset = result.getOffsetForPosition(pos)
          onTextAnnotationClicked(text, offset)
        }
      },
      onUpOrCancel = { currentlyPressedAnnotationPath = null }
    )
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(top = 4.dp)
  ) {
    val annotationPath = currentlyPressedAnnotationPath
    val annotationColor = currentPressedAnnotationBgColor

    if (annotationPath != null && annotationColor != null) {
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        onDraw = {
          drawPath(
            path = annotationPath,
            style = Fill,
            brush = SolidColor(value = annotationColor)
          )
        }
      )
    }

    KurobaComposeText(
      modifier = modifier.then(pressIndicator),
      color = color,
      fontSize = fontSize,
      text = text,
      fontWeight = fontWeight,
      maxLines = maxLines,
      overflow = overflow,
      softWrap = softWrap,
      enabled = enabled,
      textAlign = textAlign,
      inlineContent = inlineContent,
      onTextLayout = { result -> layoutResult = result }
    )
  }
}

@Composable
fun KurobaComposeTextButton(
  modifier: Modifier = Modifier,
  text: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  KurobaComposeButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    buttonContent = {
      Text(
        text = text,
        modifier = Modifier.fillMaxSize(),
        textAlign = TextAlign.Center
      )
    }
  )
}

@Composable
fun KurobaComposeButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  buttonContent: @Composable RowScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .wrapContentWidth()
      .height(36.dp)
      .then(modifier),
    content = buttonContent,
    colors = chanTheme.buttonColors()
  )
}

@Composable
fun KurobaComposeTextBarButton(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onClick: () -> Unit,
  text: String,
) {
  val chanTheme = LocalChanTheme.current

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .wrapContentSize()
      .then(modifier),
    content = {
      val textColor = if (enabled) {
        chanTheme.textColorPrimaryCompose
      } else {
        chanTheme.textColorPrimaryCompose.copy(alpha = ContentAlpha.disabled)
      }

      Text(
        text = text.uppercase(Locale.ENGLISH),
        color = textColor,
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.CenterVertically),
        textAlign = TextAlign.Center
      )
    },
    elevation = null,
    colors = chanTheme.barButtonColors()
  )
}

@Composable
fun KurobaComposeDivider(
  modifier: Modifier = Modifier,
  thickness: Dp = 1.dp,
  startIndent: Dp = 0.dp
) {
  val chanTheme = LocalChanTheme.current

  val indentMod = if (startIndent.value != 0f) {
    Modifier.padding(start = startIndent)
  } else {
    Modifier
  }

  val targetThickness = if (thickness == Dp.Hairline) {
    (1f / LocalDensity.current.density).dp
  } else {
    thickness
  }

  Box(
    modifier
      .then(indentMod)
      .height(targetThickness)
      .background(color = chanTheme.dividerColorCompose)
  )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.kurobaClickable(
  enabled: Boolean = true,
  hasClickIndication: Boolean = true,
  bounded: Boolean = true,
  onLongClick: (() -> Unit)? = null,
  onClick: (() -> Unit)? = null
): Modifier {
  if (onLongClick == null && onClick == null) {
    error("At least one of the callbacks must be non-null")
  }

  return composed {
    val chanTheme = LocalChanTheme.current

    val indication = if (enabled && hasClickIndication) {
      val color = remember(key1 = chanTheme) {
        if (chanTheme.isLightTheme) {
          Color(0x40000000)
        } else {
          Color(0x40ffffff)
        }
      }

      rememberRipple(bounded = bounded, color = color)
    } else {
      null
    }

    return@composed then(
      Modifier.combinedClickable(
        enabled = enabled,
        indication = indication,
        interactionSource = remember { MutableInteractionSource() },
        onLongClick = onLongClick,
        onClick = onClick ?: defaultNoopClickCallback
      )
    )
  }
}

@Composable
fun KurobaComposeCardView(
  modifier: Modifier = Modifier,
  backgroundColor: Color? = null,
  shape: Shape = RoundedCornerShape(2.dp),
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Card(
    modifier = modifier,
    shape = shape,
    backgroundColor = backgroundColor ?: chanTheme.backColorCompose
  ) {
    content()
  }
}

@Composable
fun KurobaComposeIcon(
  modifier: Modifier = Modifier,
  @DrawableRes drawableId: Int,
  colorBehindIcon: Color? = null
) {
  val chanTheme = LocalChanTheme.current
  val tintColor = remember(key1 = chanTheme) {
    if (colorBehindIcon == null) {
      Color(ThemeEngine.resolveDrawableTintColor(chanTheme))
    } else {
      Color(ThemeEngine.resolveDrawableTintColor(ThemeEngine.isDarkColor(colorBehindIcon.value)))
    }
  }

  Image(
    modifier = modifier,
    painter = painterResource(id = drawableId),
    colorFilter = ColorFilter.tint(tintColor),
    contentDescription = null
  )
}

fun Modifier.consumeClicks(consume: Boolean = true): Modifier {
  if (!consume) {
    return this
  }

  return composed {
    clickable(
      interactionSource = remember { MutableInteractionSource() },
      indication = null,
      onClick = { /** no-op */ }
    )
  }
}


@Composable
fun KurobaComposeTextField(
  value: String,
  modifier: Modifier = Modifier,
  onValueChange: (String) -> Unit,
  maxLines: Int = Int.MAX_VALUE,
  singleLine: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions(),
  textStyle: TextStyle = LocalTextStyle.current,
  enabled: Boolean = true,
  label: @Composable (() -> Unit)? = null
) {
  val chanTheme = LocalChanTheme.current
  val view = LocalView.current

  DisposableEffect(
    key1 = view,
    effect = {
      if (view.isAttachedToWindow) {
        view.requestApplyInsets()
      }

      onDispose {
        if (view.isAttachedToWindow) {
          view.requestApplyInsets()
        }
      }
    }
  )

  val textSelectionColors = remember(key1 = chanTheme.accentColorCompose) {
    TextSelectionColors(
      handleColor = chanTheme.accentColorCompose,
      backgroundColor = chanTheme.accentColorCompose.copy(alpha = 0.4f)
    )
  }

  CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
    TextField(
      modifier = modifier,
      enabled = enabled,
      value = value,
      label = label,
      onValueChange = onValueChange,
      maxLines = maxLines,
      singleLine = singleLine,
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      colors = chanTheme.textFieldColors(),
      textStyle = textStyle
    )
  }
}

@Composable
fun KurobaComposeCheckbox(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  text: String? = null,
  currentlyChecked: Boolean,
  onCheckChanged: (Boolean) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  var isChecked by remember(key1 = currentlyChecked) { mutableStateOf(currentlyChecked) }

  val color = remember(key1 = chanTheme) {
    if (chanTheme.isLightTheme) {
      Color(0x40000000)
    } else {
      Color(0x40ffffff)
    }
  }

  Row(
    modifier = Modifier
      .clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = rememberRipple(bounded = true, color = color),
        onClick = {
          isChecked = isChecked.not()
          onCheckChanged(isChecked)
        }
      )
      .padding(vertical = 4.dp)
      .then(modifier)
  ) {
    Checkbox(
      modifier = Modifier.align(Alignment.CenterVertically),
      checked = isChecked,
      enabled = enabled,
      onCheckedChange = { checked ->
        isChecked = checked
        onCheckChanged(isChecked)
      },
      colors = chanTheme.checkBoxColors()
    )

    if (text != null) {
      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeText(
        modifier = Modifier.align(Alignment.CenterVertically),
        text = text,
        enabled = enabled
      )
    }
  }
}