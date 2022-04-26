package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KurobaComposeCustomTextField(
  value: TextFieldValue,
  onValueChange: (TextFieldValue) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  textColor: Color = Color.Unspecified,
  parentBackgroundColor: Color = Color.Unspecified,
  drawBottomIndicator: Boolean = true,
  fontSize: TextUnit = 16.sp,
  maxLines: Int = Int.MAX_VALUE,
  singleLine: Boolean = false,
  labelText: String? = null,
  maxTextLength: Int = Int.MAX_VALUE,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions(),
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
  val chanTheme = LocalChanTheme.current
  val textFieldColors = chanTheme.textFieldColors()
  val cursorBrush = remember(key1 = chanTheme) { SolidColor(chanTheme.accentColorCompose) }
  val lineTotalHeight = if (drawBottomIndicator) 4.dp else 0.dp
  val labelTextBottomOffset = if (drawBottomIndicator) 2.dp else 0.dp
  val disabledContentAlpha = ContentAlpha.disabled

  val actualTextColor = if (!textColor.isUnspecified) {
    textColor
  } else {
    if (ThemeEngine.isDarkColor(parentBackgroundColor)) {
      Color.White
    } else {
      Color.Black
    }
  }

  val textStyle = remember(key1 = actualTextColor, key2 = fontSize, key3 = enabled) {
    val textColorWithAlpha = if (enabled) {
      actualTextColor
    } else {
      actualTextColor.copy(alpha = disabledContentAlpha)
    }

    return@remember TextStyle.Default.copy(color = textColorWithAlpha, fontSize = fontSize)
  }

  val indicatorLineModifier = if (drawBottomIndicator) {
    val indicatorColorState = textFieldColors.indicatorColor(
      enabled = enabled,
      isError = false,
      interactionSource = interactionSource
    )

    Modifier.drawIndicatorLine(
      color = indicatorColorState.value,
      lineWidth = 2.dp,
      verticalOffset = 2.dp
    )
  } else {
    Modifier
  }

  val textSelectionColors = remember(key1 = chanTheme.accentColorCompose) {
    TextSelectionColors(
      handleColor = chanTheme.accentColorCompose,
      backgroundColor = chanTheme.accentColorCompose.copy(alpha = 0.4f)
    )
  }

  var localInput by remember { mutableStateOf(value) }

  KurobaComposeCustomTextFieldInternal(
    modifier = modifier,
    labelText = labelText,
    labelTextBottomOffset = labelTextBottomOffset,
    maxTextLength = maxTextLength,
    labelTextContent = {
      val isFocused by interactionSource.collectIsFocusedAsState()

      AnimatedVisibility(
        visible = !enabled || (!isFocused && localInput.text.isEmpty()),
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        val alpha = if (enabled) {
          ContentAlpha.medium
        } else {
          ContentAlpha.disabled
        }

        val hintColor = remember(key1 = parentBackgroundColor, key2 = alpha) {
          if (parentBackgroundColor.isUnspecified) {
            Color.DarkGray.copy(alpha = alpha)
          } else {
            if (ThemeEngine.isDarkColor(parentBackgroundColor)) {
              Color.LightGray.copy(alpha = alpha)
            } else {
              Color.DarkGray.copy(alpha = alpha)
            }
          }
        }

        Text(
          text = labelText!!,
          fontSize = fontSize,
          color = hintColor
        )
      }
    },
    textFieldContent = {
      CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
        BasicTextField(
          modifier = indicatorLineModifier
            .padding(bottom = lineTotalHeight),
          enabled = enabled,
          textStyle = textStyle,
          singleLine = singleLine,
          maxLines = maxLines,
          cursorBrush = cursorBrush,
          value = value,
          keyboardOptions = keyboardOptions,
          keyboardActions = keyboardActions,
          visualTransformation = visualTransformation,
          interactionSource = interactionSource,
          onValueChange = { text ->
            localInput = text
            onValueChange(text)
          }
        )
      }
    },
    textCounterContent = {
      val currentCounter = localInput.text.length
      val maxCounter = maxTextLength
      val counterText = remember(key1 = currentCounter, key2 = maxCounter) { "$currentCounter / $maxCounter" }
      val counterTextColor = if (currentCounter > maxCounter) {
        chanTheme.errorColorCompose
      } else {
        chanTheme.textColorHintCompose
      }

      Column {
        Text(
          text = counterText,
          fontSize = 12.sp,
          color = counterTextColor,
        )
      }
    }
  )
}

@Composable
private fun KurobaComposeCustomTextFieldInternal(
  modifier: Modifier,
  labelText: String?,
  labelTextBottomOffset: Dp,
  maxTextLength: Int,
  labelTextContent: @Composable () -> Unit,
  textFieldContent: @Composable () -> Unit,
  textCounterContent: @Composable () -> Unit
) {
  val componentsCount = 3
  val labelTextSlotId = 0
  val textCounterSlotId = 1
  val textSlotId = 2

  val labelTextBottomOffsetPx = with(LocalDensity.current) { labelTextBottomOffset.toPx().toInt() }

  SubcomposeLayout(
    modifier = modifier,
    measurePolicy = { constraints ->
      val measurables = arrayOfNulls<Measurable?>(componentsCount)

      if (labelText != null) {
        val labelTextMeasurable = this.subcompose(
          slotId = labelTextSlotId,
          content = { labelTextContent() }
        ).firstOrNull()

        measurables[labelTextSlotId] = labelTextMeasurable
      }

      if (maxTextLength != Int.MAX_VALUE) {
        val textCounterMeasurable = this.subcompose(
          slotId = textCounterSlotId,
          content = { textCounterContent() }
        ).firstOrNull()

        measurables[textCounterSlotId] = textCounterMeasurable
      }

      val textFieldMeasurable = this.subcompose(
        slotId = textSlotId,
        content = { textFieldContent() }
      ).firstOrNull()

      measurables[textSlotId] = textFieldMeasurable

      var maxHeight = 0
      val placeables = arrayOfNulls<Placeable>(componentsCount)

      measurables[labelTextSlotId]?.let { labelTextMeasurable ->
        val placeable = labelTextMeasurable.measure(constraints)
        maxHeight = Math.max(maxHeight, placeable.height)

        placeables[labelTextSlotId] = placeable
      }

      // We are always supposed to at least have the text
      measurables[textSlotId]!!.let { textMeasurable ->
        val textCounterPlaceable = measurables[textCounterSlotId]?.let { textCounterMeasurable ->
          val placeable = textCounterMeasurable.measure(Constraints(maxWidth = constraints.maxWidth))
          placeables[textCounterSlotId] = placeable

          return@let placeable
        }

        val textCounterHeight = (textCounterPlaceable?.height ?: 0)
        val newMaxHeight = (constraints.maxHeight - textCounterHeight).coerceAtLeast(constraints.minHeight)

        val textPlaceable = textMeasurable.measure(constraints.copy(maxHeight = newMaxHeight))
        placeables[textSlotId] = textPlaceable

        maxHeight = Math.max(
          maxHeight,
          textPlaceable.height + textCounterHeight
        )
      }

      layout(constraints.maxWidth, maxHeight) {
        for ((index, placeable) in placeables.withIndex()) {
          if (placeable == null) {
            continue
          }

          when (index) {
            labelTextSlotId -> {
              if (maxHeight > placeable.height + labelTextBottomOffsetPx) {
                val y = maxHeight - (placeable.height + labelTextBottomOffsetPx)
                placeable.placeRelative(0, y)
              } else {
                placeable.placeRelative(0, 0)
              }
            }
            textCounterSlotId -> {
              placeable.placeRelative(0, 0)
            }
            textSlotId -> {
              val textCounterHeight = placeables[textCounterSlotId]?.height ?: 0
              placeable.placeRelative(0, textCounterHeight)
            }
            else -> {
              // no-op
            }
          }
        }
      }
    }
  )
}

private fun Modifier.drawIndicatorLine(
  color: Color,
  lineWidth: Dp = 1.dp,
  verticalOffset: Dp = Dp.Unspecified
): Modifier {
  return drawBehind {
    val strokeWidth = lineWidth.value * density
    val y = size.height - strokeWidth / 2

    val drawFunc = {
      drawLine(
        color,
        Offset(0f, y),
        Offset(size.width, y),
        strokeWidth
      )
    }

    if (verticalOffset.isSpecified) {
      translate(top = verticalOffset.toPx()) {
        drawFunc()
      }
    } else {
      drawFunc()
    }
  }
}