package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ButtonElevation
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.detectTapGesturesWithFilter
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.drawIndicatorLine
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import java.util.*

private val DefaultFillMaxSizeModifier: Modifier = Modifier.fillMaxSize()
private val DefaultNoopClickCallback = { }

private const val DarkRippleColor = 0x40000000
private const val LightRippleColor = 0x40ffffff

@Composable
fun KurobaComposeLoadingIndicator(
  modifier: Modifier = DefaultFillMaxSizeModifier,
  overrideColor: Color? = null,
  indicatorSize: Dp = 42.dp,
  fadeInTimeMs: Long = 300
) {
  var showLoadingIndicator by remember {
    val showByDefault = fadeInTimeMs <= 0
    return@remember mutableStateOf(showByDefault)
  }

  val animationAlpha by animateFloatAsState(targetValue = if (showLoadingIndicator) 1f else 0f)

  if (fadeInTimeMs > 0) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        delay(fadeInTimeMs)
        showLoadingIndicator = true
      }
    )
  }

  Box(
    modifier = modifier.then(
      Modifier.graphicsLayer { alpha = animationAlpha }
    )
  ) {
    val color = if (overrideColor == null) {
      val chanTheme = LocalChanTheme.current
      chanTheme.accentColor
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
fun KurobaComposeLoadingIndicator(
  modifier: Modifier = DefaultFillMaxSizeModifier,
  @FloatRange(from = 0.0, to = 1.0) progress: Float,
  overrideColor: Color? = null,
  indicatorSize: Dp = 42.dp
) {
  Box(modifier = modifier) {
    val color = if (overrideColor == null) {
      val chanTheme = LocalChanTheme.current
      chanTheme.accentColor
    } else {
      overrideColor
    }

    CircularProgressIndicator(
      progress = progress,
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
    KurobaComposeText(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth(),
      text = errorMessage,
      fontSize = 16.sp,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(32.dp))

    KurobaComposeTextButton(
      modifier = Modifier.padding(horizontal = 24.dp),
      text = buttonText,
      onClick = onButtonClicked
    )
  }
}

@Composable
fun KurobaComposeMessage(
  modifier: Modifier = DefaultFillMaxSizeModifier,
  message: String
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    KurobaComposeText(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth(),
      text = message,
      fontSize = 16.sp,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
fun KurobaComposeThemeDependantText(
  text: String,
  modifier: Modifier = Modifier,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontStyle: FontStyle? = null,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  textAlign: TextAlign? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
) {
  val fontSizeKurobaUnits = rememberKurobaTextUnit(fontSize = fontSize)

  KurobaComposeThemeDependantText(
    text = text,
    modifier = modifier,
    fontSize = fontSizeKurobaUnits,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
  )
}

@Composable
fun KurobaComposeThemeDependantText(
  text: String,
  modifier: Modifier = Modifier,
  fontSize: KurobaTextUnit = rememberKurobaTextUnit(fontSize = 16.sp),
  fontStyle: FontStyle? = null,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  textAlign: TextAlign? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
) {
  val chanTheme = LocalChanTheme.current

  val textColor = remember(key1 = chanTheme.backColor) {
    ThemeEngine.resolveDarkOrLightColor(chanTheme.backColor)
  }

  KurobaComposeCustomUnitText(
    text = text,
    modifier = modifier,
    color = textColor,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
  )
}

@Composable
fun KurobaComposeText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontStyle: FontStyle? = null,
  fontSize: TextUnit = 16.sp,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  inlineContent: ImmutableMap<String, InlineTextContent> = persistentMapOf()
) {
  val fontSizeInKurobaUnits = rememberKurobaTextUnit(fontSize = fontSize)

  KurobaComposeCustomUnitText(
    text = text,
    modifier = modifier,
    color = color,
    fontStyle = fontStyle,
    fontSize = fontSizeInKurobaUnits,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    maxLines = maxLines,
    minLines = minLines,
    overflow = overflow,
    softWrap = softWrap,
    enabled = enabled,
    textAlign = textAlign,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    lineHeight = lineHeight,
    inlineContent = inlineContent,
  )
}

@Composable
fun KurobaComposeCustomUnitText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontStyle: FontStyle? = null,
  fontSize: KurobaTextUnit = rememberKurobaTextUnit(fontSize = 16.sp),
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  inlineContent: ImmutableMap<String, InlineTextContent> = persistentMapOf()
) {
  val annotatedText = remember(key1 = text) { AnnotatedString(text) }

  KurobaComposeCustomUnitText(
    text = annotatedText,
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    maxLines = maxLines,
    minLines = minLines,
    overflow = overflow,
    softWrap = softWrap,
    enabled = enabled,
    textAlign = textAlign,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    inlineContent = inlineContent
  )
}

@Composable
fun KurobaComposeText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: TextUnit = 16.sp,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  fontStyle: FontStyle? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  inlineContent: ImmutableMap<String, InlineTextContent> = persistentMapOf(),
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val fontSizeInKurobaUnits = rememberKurobaTextUnit(fontSize = fontSize)

  KurobaComposeCustomUnitText(
    text = text,
    modifier = modifier,
    color = color,
    fontSize = fontSizeInKurobaUnits,
    fontWeight = fontWeight,
    maxLines = maxLines,
    minLines = minLines,
    overflow = overflow,
    softWrap = softWrap,
    enabled = enabled,
    textAlign = textAlign,
    fontStyle = fontStyle,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    lineHeight = lineHeight,
    inlineContent = inlineContent,
    onTextLayout = onTextLayout,
  )
}

@Composable
fun KurobaComposeCustomUnitText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  color: Color? = null,
  fontSize: KurobaTextUnit = KurobaTextUnit(16.sp),
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  textAlign: TextAlign? = null,
  fontStyle: FontStyle? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  inlineContent: ImmutableMap<String, InlineTextContent> = persistentMapOf(),
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val textColorPrimary = if (color == null || color.isUnspecified) {
    val chanTheme = LocalChanTheme.current
    chanTheme.textColorPrimary
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
    fontSize = collectTextFontSize(defaultFontSize = fontSize),
    fontStyle = fontStyle,
    fontFamily = fontFamily,
    maxLines = maxLines,
    minLines = minLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    lineHeight = lineHeight,
    inlineContent = inlineContent,
    onTextLayout = onTextLayout
  )
}

@Composable
fun KurobaComposeClickableText(
  modifier: Modifier = Modifier,
  text: AnnotatedString,
  color: Color? = null,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  enabled: Boolean = true,
  isTextClickable: Boolean = true,
  textAlign: TextAlign? = null,
  inlineContent: ImmutableMap<String, InlineTextContent> = persistentMapOf(),
  annotationBgColors: ImmutableMap<String, Color> = persistentMapOf(),
  detectClickedAnnotations: (Offset, TextLayoutResult?, AnnotatedString) -> AnnotatedString.Range<String>?,
  onTextAnnotationClicked: (AnnotatedString, Int) -> Unit,
  onTextAnnotationLongClicked: (AnnotatedString, Int) -> Unit,
  onTextLayout: (TextLayoutResult) -> Unit = {}
) {
  var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  var currentlyPressedAnnotationPath by remember { mutableStateOf<Path?>(null) }
  var currentPressedAnnotationBgColor by remember { mutableStateOf<Color?>(null) }

  val pointerInputModifier = if (isTextClickable) {
    Modifier.pointerInput(key1 = text) {
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
        onLongPress = { pos ->
          currentlyPressedAnnotationPath = null

          layoutResult?.let { result ->
            val offset = result.getOffsetForPosition(pos)
            onTextAnnotationLongClicked(text, offset)
          }
        },
        onUpOrCancel = { currentlyPressedAnnotationPath = null }
      )
    }
  } else {
    Modifier
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
      modifier = modifier.then(pointerInputModifier),
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
      onTextLayout = { result ->
        layoutResult = result
        onTextLayout(result)
      }
    )
  }
}

@Composable
fun KurobaComposeTextButton(
  modifier: Modifier = Modifier,
  text: String,
  enabled: Boolean = true,
  fontSize: TextUnit = TextUnit.Unspecified,
  onClick: () -> Unit,
) {
  KurobaComposeButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    buttonContent = {
      KurobaComposeText(
        text = text,
        modifier = Modifier.wrapContentSize(),
        color = Color.White,
        fontSize = fontSize,
        textAlign = TextAlign.Center
      )
    }
  )
}

@Composable
private fun KurobaComposeButton(
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
      .heightIn(min = 36.dp)
      .then(modifier),
    content = buttonContent,
    colors = chanTheme.buttonColors()
  )
}

private val ContentPadding = PaddingValues(
  start = 8.dp,
  top = 4.dp,
  end = 8.dp,
  bottom = 4.dp
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun KurobaComposeCustomButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  elevation: ButtonElevation? = ButtonDefaults.elevation(),
  shape: Shape = MaterialTheme.shapes.small,
  border: BorderStroke? = null,
  colors: ButtonColors = ButtonDefaults.buttonColors(),
  contentPadding: PaddingValues = ContentPadding,
  content: @Composable RowScope.() -> Unit
) {
  val contentColor by colors.contentColor(enabled)
  Surface(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    color = colors.backgroundColor(enabled).value,
    contentColor = contentColor.copy(alpha = 1f),
    border = border,
    elevation = elevation?.elevation(enabled, interactionSource)?.value ?: 0.dp,
    interactionSource = interactionSource,
  ) {
    CompositionLocalProvider(LocalContentAlpha provides contentColor.alpha) {
      ProvideTextStyle(
        value = MaterialTheme.typography.button
      ) {
        Row(
          Modifier
            .padding(contentPadding),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
          content = content
        )
      }
    }
  }
}

@Composable
fun KurobaComposeTextBarButton(
  modifier: Modifier = Modifier,
  text: String,
  enabled: Boolean = true,
  customTextColor: Color? = null,
  fontSize: TextUnit = 14.sp,
  onClick: () -> Unit,
) {
  val chanTheme = LocalChanTheme.current

  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    content = {
      val textColor = customTextColor
        ?: chanTheme.textColorPrimary

      val modifiedTextColor = if (enabled) {
        textColor
      } else {
        textColor.copy(alpha = ContentAlpha.disabled)
      }

      KurobaComposeText(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.CenterVertically),
        text = text.uppercase(Locale.ENGLISH),
        color = modifiedTextColor,
        fontSize = fontSize,
        textAlign = TextAlign.Center
      )
    },
    elevation = null,
    colors = chanTheme.barButtonColors()
  )
}

@Composable
fun KurobaComposeTextBarButton(
  modifier: Modifier = Modifier,
  text: AnnotatedString,
  enabled: Boolean = true,
  customTextColor: Color? = null,
  fontSize: TextUnit = 14.sp,
  onClick: () -> Unit,
) {
  val chanTheme = LocalChanTheme.current

  KurobaComposeCustomButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    elevation = null,
    colors = chanTheme.barButtonColors(),
    content = {
      val textColor = customTextColor
        ?: chanTheme.textColorPrimary

      val modifiedTextColor = if (enabled) {
        textColor
      } else {
        textColor.copy(alpha = ContentAlpha.disabled)
      }

      KurobaComposeText(
        text = text,
        color = modifiedTextColor,
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.CenterVertically),
        textAlign = TextAlign.Center,
        fontSize = fontSize
      )
    },
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

  val dividerColorWithAlpha = remember(key1 = chanTheme.dividerColor) {
    chanTheme.dividerColor.copy(alpha = 0.1f)
  }

  Box(
    modifier
      .then(indentMod)
      .height(targetThickness)
      .background(color = dividerColorWithAlpha)
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

  if (!enabled) {
    return Modifier
  }

  return composed {
    val chanTheme = LocalChanTheme.current

    val indication = if (hasClickIndication) {
      val color = remember(key1 = chanTheme) {
        if (chanTheme.isLightTheme) {
          Color(DarkRippleColor)
        } else {
          Color(LightRippleColor)
        }
      }

      rememberRipple(bounded = bounded, color = color)
    } else {
      null
    }

    return@composed then(
      Modifier.combinedClickable(
        indication = indication,
        interactionSource = remember { MutableInteractionSource() },
        onLongClick = onLongClick,
        onClick = onClick ?: DefaultNoopClickCallback
      )
    )
  }
}

@Composable
fun KurobaComposeCard(
  modifier: Modifier = Modifier,
  backgroundColor: Color? = null,
  shape: Shape = RoundedCornerShape(2.dp),
  elevation: Dp = 1.dp,
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Card(
    modifier = modifier,
    shape = shape,
    backgroundColor = backgroundColor ?: chanTheme.backColorSecondary,
    elevation = elevation
  ) {
    content()
  }
}

@Composable
fun KurobaComposeClickableIcon(
  @DrawableRes drawableId: Int,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colorBehindIcon: Color? = null,
  iconColor: Color? = null,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null
) {
  val chanTheme = LocalChanTheme.current
  val alpha = if (enabled) DefaultAlpha else ContentAlpha.disabled

  val tintColor = remember(
    key1 = chanTheme.backColor,
    key2 = colorBehindIcon,
    key3 = iconColor
  ) {
    if (iconColor != null) {
      return@remember iconColor
    }

    if (colorBehindIcon == null) {
      ThemeEngine.resolveDarkOrLightColor(chanTheme)
    } else {
      ThemeEngine.resolveDarkOrLightColor(ThemeEngine.isDarkColor(colorBehindIcon.value))
    }
  }

  val clickModifier = if (enabled) {
    Modifier.kurobaClickable(
      bounded = false,
      onClick = onClick,
      onLongClick = onLongClick
    )
  } else {
    Modifier
  }

  Image(
    modifier = modifier.then(clickModifier),
    painter = painterResource(id = drawableId),
    colorFilter = ColorFilter.tint(tintColor),
    alpha = alpha,
    contentDescription = null
  )
}

@Composable
fun KurobaComposeIcon(
  modifier: Modifier = Modifier,
  @DrawableRes drawableId: Int,
  colorBehindIcon: Color? = null,
  iconColor: Color? = null,
  enabled: Boolean = true,
  contentScale: ContentScale = ContentScale.Fit,
) {
  val chanTheme = LocalChanTheme.current
  val alpha = if (enabled) DefaultAlpha else ContentAlpha.disabled

  val tintColor = remember(
    key1 = chanTheme.backColor,
    key2 = colorBehindIcon,
    key3 = iconColor
  ) {
    if (iconColor != null) {
      return@remember iconColor
    }

    if (colorBehindIcon == null) {
      ThemeEngine.resolveDarkOrLightColor(chanTheme)
    } else {
      ThemeEngine.resolveDarkOrLightColor(ThemeEngine.isDarkColor(colorBehindIcon.value))
    }
  }

  Image(
    modifier = Modifier
      .graphicsLayer { this.alpha = alpha }
      .then(modifier),
    painter = painterResource(id = drawableId),
    colorFilter = ColorFilter.tint(tintColor),
    contentScale = contentScale,
    contentDescription = null
  )
}

fun Modifier.consumeClicks(enabled: Boolean = true): Modifier {
  if (!enabled) {
    return this
  }

  return pointerInput(key1 = Unit) { }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.passClicksThrough(passClicks: Boolean = true): Modifier {
  if (!passClicks) {
    return this
  }

  return pointerInteropFilter(onTouchEvent = { false })
}

@Composable
fun KurobaComposeTextField(
  value: TextFieldValue,
  modifier: Modifier = Modifier,
  onValueChange: (TextFieldValue) -> Unit,
  fontSize: KurobaTextUnit = KurobaTextUnit(16.sp),
  maxLines: Int = Int.MAX_VALUE,
  singleLine: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions(),
  textStyle: TextStyle = LocalTextStyle.current,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  isError: Boolean = false,
  shape: Shape = TextFieldDefaults.TextFieldShape,
  label: @Composable ((InteractionSource) -> Unit)? = null,
  placeholder: @Composable (() -> Unit)? = null,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
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

  val textSelectionColors = remember(key1 = chanTheme.accentColor) {
    TextSelectionColors(
      handleColor = Color.Transparent,
      backgroundColor = chanTheme.accentColor.copy(alpha = 0.4f)
    )
  }

  CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
    val textFontSize = collectTextFontSize(defaultFontSize = fontSize)
    val colors = chanTheme.textFieldColors()

    // If color is not provided via the text style, use content color as a default
    val textColor = textStyle.color.takeOrElse { colors.textColor(enabled).value }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor, fontSize = textFontSize))

    val isFocused by interactionSource.collectIsFocusedAsState()

    @OptIn(ExperimentalMaterialApi::class)
    BasicTextField(
      value = value,
      modifier = modifier
        .background(colors.backgroundColor(enabled).value, shape)
        .drawIndicatorLine(
          enabled = enabled,
          isError = isError,
          isFocused = isFocused,
          lineWidth = 2.dp
        )
        .defaultMinSize(
          minWidth = TextFieldDefaults.MinWidth,
          minHeight = TextFieldDefaults.MinHeight
        ),
      onValueChange = onValueChange,
      enabled = enabled,
      readOnly = readOnly,
      textStyle = mergedTextStyle,
      cursorBrush = SolidColor(colors.cursorColor(isError).value),
      visualTransformation = visualTransformation,
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      interactionSource = interactionSource,
      singleLine = singleLine,
      maxLines = maxLines,
      decorationBox = @Composable { innerTextField ->
        val labelFunc: (@Composable (() -> Unit))? = if (label == null) {
          null
        } else {
          { label(interactionSource) }
        }

        TextFieldDefaults.TextFieldDecorationBox(
          value = value.text,
          visualTransformation = visualTransformation,
          innerTextField = innerTextField,
          placeholder = placeholder,
          label = labelFunc,
          leadingIcon = leadingIcon,
          trailingIcon = trailingIcon,
          singleLine = singleLine,
          enabled = enabled,
          isError = isError,
          interactionSource = interactionSource,
          colors = colors,
          contentPadding = remember { PaddingValues(4.dp) }
        )
      }
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
      Color(DarkRippleColor)
    } else {
      Color(LightRippleColor)
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

@Composable
fun KurobaComposeRadioButton(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  text: String? = null,
  currentlySelected: Boolean,
  onSelectionChanged: (Boolean) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  var selected by remember(key1 = currentlySelected) { mutableStateOf(currentlySelected) }

  val color = remember(key1 = chanTheme) {
    if (chanTheme.isLightTheme) {
      Color(DarkRippleColor)
    } else {
      Color(LightRippleColor)
    }
  }

  Row(
    modifier = Modifier
      .clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = rememberRipple(bounded = true, color = color),
        onClick = {
          selected = selected.not()
          onSelectionChanged(selected)
        }
      )
      .padding(vertical = 4.dp)
      .then(modifier)
  ) {
    RadioButton(
      modifier = Modifier.align(Alignment.CenterVertically),
      selected = selected,
      enabled = enabled,
      colors = chanTheme.radioButtonColors(),
      onClick = {
        selected = selected.not()
        onSelectionChanged(selected)
      }
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

@Composable
fun KurobaFloatingActionButton(
  modifier: Modifier = Modifier,
  @DrawableRes iconDrawableId: Int,
  backgroundColor: Color? = null,
  horizOffset: Dp? = null,
  vertOffset: Dp? = null,
  fabSize: Dp? = null,
  onClick: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val actualFabSize = fabSize ?: dimensionResource(id = R.dimen.fab_size)

  val offsetModifier = if (horizOffset != null || vertOffset != null) {
    Modifier.offset(x = horizOffset ?: 0.dp, y = vertOffset ?: 0.dp)
  } else {
    Modifier
  }

  val bgColor = backgroundColor ?: chanTheme.accentColor
  val contentColor = remember(key1 = bgColor) { ThemeEngine.resolveFabContentColor(bgColor) }

  FloatingActionButton(
    modifier = modifier.then(
      Modifier
        .size(actualFabSize)
        .then(offsetModifier)
    ),
    backgroundColor = bgColor,
    contentColor = contentColor,
    onClick = onClick
  ) {
    KurobaComposeIcon(
      drawableId = iconDrawableId,
      iconColor = contentColor
    )
  }
}

@Composable
fun KurobaComposeSwitch(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Switch(
    modifier = modifier,
    enabled = enabled,
    checked = checked,
    onCheckedChange = onCheckedChange,
    colors = chanTheme.switchColors()
  )
}

@Composable
fun Collapsable(
  title: String,
  collapsedByDefault: Boolean = true,
  content: @Composable () -> Unit
) {
  var collapsed by rememberSaveable { mutableStateOf(collapsedByDefault) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .animateContentSize()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(onClick = { collapsed = !collapsed }),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .graphicsLayer { rotationZ = if (collapsed) 0f else 90f },
        drawableId = R.drawable.ic_baseline_arrow_right_24
      )

      Spacer(modifier = Modifier.width(4.dp))

      KurobaComposeText(text = title)

      Spacer(modifier = Modifier.width(4.dp))

      KurobaComposeDivider(modifier = Modifier
        .weight(1f)
        .height(1.dp))
    }

    if (!collapsed) {
      Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        content()
      }
    }
  }
}

@Stable
@Composable
fun rememberRipple(
  bounded: Boolean = true,
  radius: Dp = Dp.Unspecified,
  color: Color = Color.Unspecified
): IndicationNodeFactory {
  return remember(bounded, radius, color) { ripple(bounded, radius, color) }
}
