package com.github.k1rakishou.kurobaexlite.ui.themes

import android.annotation.SuppressLint
import android.graphics.Typeface
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CheckboxColors
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.RadioButtonColors
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.material.SliderDefaults.DisabledTickAlpha
import androidx.compose.material.SliderDefaults.InactiveTrackAlpha
import androidx.compose.material.SliderDefaults.TickAlpha
import androidx.compose.material.SwitchColors
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine.Companion.manipulateColor

@SuppressLint("ResourceType")
@Stable
abstract class ChanTheme {
  // Don't forget to update ThemeParser's gson when this class changes !!!
  abstract val name: String
  abstract val isLightTheme: Boolean
  abstract val lightStatusBar: Boolean
  abstract val lightNavBar: Boolean
  abstract val accentColor: Color
  abstract val gradientTopColor: Color
  abstract val gradientBottomColor: Color
  abstract val behindGradientColor: Color
  abstract val backColor: Color
  abstract val backColorSecondary: Color
  abstract val selectedOnBackColor: Color
  abstract val highlightedOnBackColor: Color
  abstract val errorColor: Color
  abstract val textColorPrimary: Color
  abstract val textColorSecondary: Color
  abstract val textColorHint: Color
  abstract val postSavedReplyColor: Color
  abstract val postSubjectColor: Color
  abstract val postDetailsColor: Color
  abstract val postNameColor: Color
  abstract val postInlineQuoteColor: Color
  abstract val postQuoteColor: Color
  abstract val postLinkColor: Color
  abstract val postSpoilerColor: Color
  abstract val postSpoilerRevealTextColor: Color
  abstract val dividerColor: Color
  abstract val scrollbarTrackColor: Color
  abstract val scrollbarThumbColorNormal: Color
  abstract val scrollbarThumbColorDragged: Color
  abstract val bookmarkCounterHasRepliesColor: Color
  abstract val bookmarkCounterNormalColor: Color

  abstract fun fullCopy(): ChanTheme

  val isDarkTheme: Boolean
    get() = !isLightTheme

  val isBackColorDark: Boolean
    get() = ThemeEngine.isDarkColor(backColor)
  val isBackColorLight: Boolean
    get() = !isBackColorDark

  open val mainFont: Typeface = ROBOTO_MEDIUM

  val defaultColors by lazy { loadDefaultColors() }
  val defaultBoldTypeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

  private fun loadDefaultColors(): DefaultColors {
    val controlNormalColor = if (isLightTheme) {
      CONTROL_LIGHT_COLOR
    } else {
      CONTROL_DARK_COLOR
    }

    val disabledControlAlpha = (255f * .4f).toInt()

    return DefaultColors(
      disabledControlAlpha = disabledControlAlpha,
      controlNormalColor = Color(controlNormalColor)
    )
  }

  @Composable
  fun textFieldColors(): TextFieldColors {
    val disabledAlpha = ContentAlpha.disabled

    val backColorDisabled = remember(key1 = backColor) { backColor.copy(alpha = disabledAlpha) }
    val iconColor = remember(key1 = backColor) { backColor.copy(alpha = TextFieldDefaults.IconOpacity) }

    return TextFieldDefaults.outlinedTextFieldColors(
      textColor = textColorPrimary,
      disabledTextColor = textColorPrimary.copy(ContentAlpha.disabled),
      backgroundColor = Color.Transparent,
      cursorColor = accentColor,
      focusedBorderColor = accentColor.copy(alpha = ContentAlpha.high),
      unfocusedBorderColor = defaultColors.controlNormalColor.copy(alpha = ContentAlpha.medium),
      disabledBorderColor = defaultColors.controlNormalColor.copy(alpha = ContentAlpha.disabled),
      focusedLabelColor = accentColor.copy(alpha = ContentAlpha.high),
      unfocusedLabelColor = defaultColors.controlNormalColor.copy(alpha = ContentAlpha.medium),
      disabledLabelColor = defaultColors.controlNormalColor.copy(ContentAlpha.disabled),
      leadingIconColor = iconColor,
      disabledLeadingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      errorLeadingIconColor = iconColor,
      trailingIconColor = iconColor,
      disabledTrailingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      placeholderColor = backColorDisabled.copy(ContentAlpha.medium),
      disabledPlaceholderColor = backColorDisabled.copy(ContentAlpha.disabled),
      errorBorderColor = errorColor,
      errorTrailingIconColor = errorColor,
      errorCursorColor = errorColor,
      errorLabelColor = errorColor,
    )
  }

  @Composable
  fun checkBoxColors(): CheckboxColors {
    return CheckboxDefaults.colors(
      checkedColor = accentColor,
      uncheckedColor = accentColor.copy(alpha = 0.6f),
      checkmarkColor = backColor,
      disabledColor = accentColor.copy(alpha = ContentAlpha.disabled),
      disabledIndeterminateColor = accentColor.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun radioButtonColors(): RadioButtonColors {
    return RadioButtonDefaults.colors(
      selectedColor = accentColor,
      unselectedColor = accentColor.copy(alpha = 0.6f),
      disabledColor = accentColor.copy(alpha = ContentAlpha.disabled),
    )
  }

  @Composable
  fun buttonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
      backgroundColor = accentColor,
      contentColor = backColor,
      disabledBackgroundColor = accentColor.copy(alpha = ContentAlpha.disabled),
      disabledContentColor = backColor.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun barButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
      backgroundColor = Color.Unspecified,
      contentColor = accentColor,
      disabledBackgroundColor = Color.Unspecified,
      disabledContentColor = accentColor.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun sliderColors(): SliderColors {
    val disabledThumbColor = accentColor.copy(alpha = ContentAlpha.disabled)
    val disabledActiveTrackColor =
      disabledThumbColor.copy(alpha = SliderDefaults.DisabledActiveTrackAlpha)
    val disabledInactiveTrackColor =
      disabledActiveTrackColor.copy(alpha = SliderDefaults.DisabledInactiveTrackAlpha)
    val activeTickColor = contentColorFor(accentColor).copy(alpha = TickAlpha)

    return SliderDefaults.colors(
      thumbColor = accentColor,
      disabledThumbColor = disabledThumbColor,
      activeTrackColor = accentColor,
      inactiveTrackColor = accentColor.copy(alpha = InactiveTrackAlpha),
      disabledActiveTrackColor = disabledActiveTrackColor,
      disabledInactiveTrackColor = disabledInactiveTrackColor,
      activeTickColor = activeTickColor,
      inactiveTickColor = accentColor.copy(alpha = TickAlpha),
      disabledActiveTickColor = activeTickColor.copy(alpha = DisabledTickAlpha),
      disabledInactiveTickColor = disabledInactiveTrackColor.copy(alpha = DisabledTickAlpha)
    )
  }

  @Composable
  fun switchColors(): SwitchColors {
    val checkedThumbColor = accentColor
    val uncheckedThumbColor = remember(key1 = defaultColors.controlNormalColor) {
      manipulateColor(defaultColors.controlNormalColor, 1.2f)
    }
    val uncheckedTrackColor = remember(key1 = defaultColors.controlNormalColor) {
      manipulateColor(defaultColors.controlNormalColor, .6f)
    }

    return SwitchDefaults.colors(
      checkedThumbColor = checkedThumbColor,
      checkedTrackColor = checkedThumbColor,
      checkedTrackAlpha = 0.54f,
      uncheckedThumbColor = uncheckedThumbColor,
      uncheckedTrackColor = uncheckedTrackColor,
      uncheckedTrackAlpha = 0.38f,
      disabledCheckedThumbColor = checkedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor),
      disabledCheckedTrackColor = checkedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor),
      disabledUncheckedThumbColor = uncheckedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor),
      disabledUncheckedTrackColor = uncheckedThumbColor
        .copy(alpha = ContentAlpha.disabled)
        .compositeOver(uncheckedThumbColor)
    )
  }

  fun getColorByColorId(chanThemeColorId: ChanThemeColorId): Color {
    return when (chanThemeColorId) {
      ChanThemeColorId.PostSubject -> postSubjectColor
      ChanThemeColorId.PostName -> postNameColor
      ChanThemeColorId.Accent -> accentColor
      ChanThemeColorId.PostInlineQuote -> postInlineQuoteColor
      ChanThemeColorId.PostQuote -> postQuoteColor
      ChanThemeColorId.BackColor -> backColor
      ChanThemeColorId.PostLink -> postLinkColor
      ChanThemeColorId.TextColor -> textColorPrimary
    }
  }

  data class DefaultColors(
    val disabledControlAlpha: Int,
    val controlNormalColor: Color,
  ) {

    val disabledControlAlphaFloat: Float
      get() = disabledControlAlpha.toFloat() / MAX_ALPHA_FLOAT

  }

  companion object {
    private val ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private const val CONTROL_LIGHT_COLOR = 0xFFAAAAAAL.toInt()
    private const val CONTROL_DARK_COLOR = 0xFFCCCCCCL.toInt()

    private const val MAX_ALPHA_FLOAT = 255f
  }
}