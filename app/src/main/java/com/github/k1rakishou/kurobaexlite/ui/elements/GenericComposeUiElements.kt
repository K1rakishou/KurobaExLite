package com.github.k1rakishou.kurobaexlite.ui.elements

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
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
  inlineContent: Map<String, InlineTextContent> = mapOf()
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
    color = actualTextColorPrimary,
    text = text,
    fontSize = fontSize,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    textAlign = textAlign,
    fontWeight = fontWeight,
    inlineContent = inlineContent,
    modifier = modifier,
  )
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
fun KurobaComposeDivider(modifier: Modifier = Modifier) {
  val chanTheme = LocalChanTheme.current

  Divider(
    modifier = modifier,
    color = chanTheme.dividerColorCompose,
    thickness = 1.dp
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