package com.github.k1rakishou.kurobaexlite.themes

import android.annotation.SuppressLint
import android.graphics.Typeface
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CheckboxColors
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ContentAlpha
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
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine.Companion.manipulateColor

@SuppressLint("ResourceType")
@Stable
abstract class ChanTheme {
  // Don't forget to update ThemeParser's gson when this class changes !!!
  abstract val name: String
  abstract val isLightTheme: Boolean
  abstract val lightStatusBar: Boolean
  abstract val lightNavBar: Boolean
  abstract val accentColor: Int
  abstract val primaryColor: Int
  abstract val backColor: Int
  abstract val backColorSecondary: Int
  abstract val selectedOnBackColor: Int
  abstract val highlighterColor: Int
  abstract val errorColor: Int
  abstract val textColorPrimary: Int
  abstract val textColorSecondary: Int
  abstract val textColorHint: Int
  abstract val postSavedReplyColor: Int
  abstract val postSubjectColor: Int
  abstract val postDetailsColor: Int
  abstract val postNameColor: Int
  abstract val postInlineQuoteColor: Int
  abstract val postQuoteColor: Int
  abstract val postHighlightQuoteColor: Int
  abstract val postLinkColor: Int
  abstract val postSpoilerColor: Int
  abstract val postSpoilerRevealTextColor: Int
  abstract val postUnseenLabelColor: Int
  abstract val dividerColor: Int
  abstract val scrollbarTrackColor: Int
  abstract val scrollbarThumbColorNormal: Int
  abstract val scrollbarThumbColorDragged: Int
  abstract val bookmarkCounterHasRepliesColor: Int
  abstract val bookmarkCounterNormalColor: Int

  abstract fun fullCopy(): ChanTheme

  val isDarkTheme: Boolean
    get() = !isLightTheme

  val isBackColorDark: Boolean
    get() = ThemeEngine.isDarkColor(backColor)
  val isBackColorLight: Boolean
    get() = !isBackColorDark

  val accentColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(accentColor) }
  val primaryColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(primaryColor) }
  val backColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(backColor) }
  val backColorSecondaryCompose by lazy(LazyThreadSafetyMode.NONE) { Color(backColorSecondary) }
  val selectedOnBackColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(selectedOnBackColor) }
  val highlighterColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(highlighterColor) }
  val errorColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(errorColor) }

  val textColorPrimaryCompose by lazy(LazyThreadSafetyMode.NONE) { Color(textColorPrimary) }
  val textColorSecondaryCompose by lazy(LazyThreadSafetyMode.NONE) { Color(textColorSecondary) }
  val textColorHintCompose by lazy(LazyThreadSafetyMode.NONE) { Color(textColorHint) }

  val scrollbarTrackColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(scrollbarTrackColor) }
  val scrollbarThumbColorNormalCompose by lazy(LazyThreadSafetyMode.NONE) { Color(scrollbarThumbColorNormal) }
  val scrollbarThumbColorDraggedCompose by lazy(LazyThreadSafetyMode.NONE) { Color(scrollbarThumbColorDragged) }

  val dividerColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(dividerColor) }
  val postSubjectColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postSubjectColor) }
  val postSavedReplyColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postSavedReplyColor) }
  val postLinkColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postLinkColor) }
  val postSpoilerColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postSpoilerColor) }
  val postSpoilerRevealTextColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postSpoilerRevealTextColor) }
  val postQuoteColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(postQuoteColor) }

  val bookmarkCounterHasRepliesColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(bookmarkCounterHasRepliesColor) }
  val bookmarkCounterNormalColorCompose by lazy(LazyThreadSafetyMode.NONE) { Color(bookmarkCounterNormalColor) }

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
      controlNormalColor = controlNormalColor,
      controlNormalColorCompose = Color(controlNormalColor)
    )
  }

  @Composable
  fun textFieldColors(): TextFieldColors {
    val disabledAlpha = ContentAlpha.disabled

    val backColorDisabled =
      remember(key1 = backColorCompose) { backColorCompose.copy(alpha = disabledAlpha) }
    val iconColor =
      remember(key1 = backColorCompose) { backColorCompose.copy(alpha = TextFieldDefaults.IconOpacity) }

    return TextFieldDefaults.outlinedTextFieldColors(
      textColor = textColorPrimaryCompose,
      disabledTextColor = textColorPrimaryCompose.copy(ContentAlpha.disabled),
      backgroundColor = Color.Transparent,
      cursorColor = accentColorCompose,
      focusedBorderColor = accentColorCompose.copy(alpha = ContentAlpha.high),
      unfocusedBorderColor = defaultColors.controlNormalColorCompose.copy(alpha = ContentAlpha.medium),
      disabledBorderColor = defaultColors.controlNormalColorCompose.copy(alpha = ContentAlpha.disabled),
      focusedLabelColor = accentColorCompose.copy(alpha = ContentAlpha.high),
      unfocusedLabelColor = defaultColors.controlNormalColorCompose.copy(alpha = ContentAlpha.medium),
      disabledLabelColor = defaultColors.controlNormalColorCompose.copy(ContentAlpha.disabled),
      leadingIconColor = iconColor,
      disabledLeadingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      errorLeadingIconColor = iconColor,
      trailingIconColor = iconColor,
      disabledTrailingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      placeholderColor = backColorDisabled.copy(ContentAlpha.medium),
      disabledPlaceholderColor = backColorDisabled.copy(ContentAlpha.disabled),
      errorBorderColor = errorColorCompose,
      errorTrailingIconColor = errorColorCompose,
      errorCursorColor = errorColorCompose,
      errorLabelColor = errorColorCompose,
    )
  }

  @Composable
  fun checkBoxColors(): CheckboxColors {
    return CheckboxDefaults.colors(
      checkedColor = accentColorCompose,
      uncheckedColor = accentColorCompose.copy(alpha = 0.6f),
      checkmarkColor = backColorCompose,
      disabledColor = accentColorCompose.copy(alpha = ContentAlpha.disabled),
      disabledIndeterminateColor = accentColorCompose.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun buttonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
      backgroundColor = accentColorCompose,
      contentColor = backColorCompose,
      disabledBackgroundColor = accentColorCompose.copy(alpha = ContentAlpha.disabled),
      disabledContentColor = backColorCompose.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun barButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
      backgroundColor = Color.Unspecified,
      contentColor = accentColorCompose,
      disabledBackgroundColor = Color.Unspecified,
      disabledContentColor = accentColorCompose.copy(alpha = ContentAlpha.disabled)
    )
  }

  @Composable
  fun sliderColors(): SliderColors {
    val disabledThumbColor = accentColorCompose.copy(alpha = ContentAlpha.disabled)
    val disabledActiveTrackColor =
      disabledThumbColor.copy(alpha = SliderDefaults.DisabledActiveTrackAlpha)
    val disabledInactiveTrackColor =
      disabledActiveTrackColor.copy(alpha = SliderDefaults.DisabledInactiveTrackAlpha)
    val activeTickColor = contentColorFor(accentColorCompose).copy(alpha = TickAlpha)

    return SliderDefaults.colors(
      thumbColor = accentColorCompose,
      disabledThumbColor = disabledThumbColor,
      activeTrackColor = accentColorCompose,
      inactiveTrackColor = accentColorCompose.copy(alpha = InactiveTrackAlpha),
      disabledActiveTrackColor = disabledActiveTrackColor,
      disabledInactiveTrackColor = disabledInactiveTrackColor,
      activeTickColor = activeTickColor,
      inactiveTickColor = accentColorCompose.copy(alpha = TickAlpha),
      disabledActiveTickColor = activeTickColor.copy(alpha = DisabledTickAlpha),
      disabledInactiveTickColor = disabledInactiveTrackColor.copy(alpha = DisabledTickAlpha)
    )
  }

  @Composable
  fun switchColors(): SwitchColors {
    val checkedThumbColor = accentColorCompose
    val uncheckedThumbColor = remember(key1 = defaultColors.controlNormalColorCompose) {
      manipulateColor(defaultColors.controlNormalColorCompose, 1.2f)
    }
    val uncheckedTrackColor = remember(key1 = defaultColors.controlNormalColorCompose) {
      manipulateColor(defaultColors.controlNormalColorCompose, .6f)
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

  fun getColorByColorId(chanThemeColorId: ChanThemeColorId): Int {
    return when (chanThemeColorId) {
      ChanThemeColorId.PostSubject -> postSubjectColor
      ChanThemeColorId.PostName -> postNameColor
      ChanThemeColorId.Accent -> accentColor
      ChanThemeColorId.PostInlineQuote -> postInlineQuoteColor
      ChanThemeColorId.PostQuote -> postQuoteColor
      ChanThemeColorId.BackColor -> backColorSecondary
      ChanThemeColorId.PostLink -> postLinkColor
      ChanThemeColorId.TextColor -> textColorPrimary
    }
  }

  data class DefaultColors(
    val disabledControlAlpha: Int,
    val controlNormalColor: Int,
    val controlNormalColorCompose: Color,
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