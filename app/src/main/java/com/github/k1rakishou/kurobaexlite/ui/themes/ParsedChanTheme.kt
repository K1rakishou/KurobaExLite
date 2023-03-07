package com.github.k1rakishou.kurobaexlite.ui.themes

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
data class ParsedChanTheme(
  override val name: String,
  override val isLightTheme: Boolean,
  override val lightStatusBar: Boolean,
  override val lightNavBar: Boolean,
  override val accentColor: Color,
  override val gradientTopColor: Color,
  override val gradientBottomColor: Color,
  override val behindGradientColor: Color,
  override val backColor: Color,
  override val backColorSecondary: Color,
  override val selectedOnBackColor: Color,
  override val highlightedOnBackColor: Color,
  override val errorColor: Color,
  override val textColorPrimary: Color,
  override val textColorSecondary: Color,
  override val textColorHint: Color,
  override val postSavedReplyColor: Color,
  override val postSubjectColor: Color,
  override val postDetailsColor: Color,
  override val postNameColor: Color,
  override val postInlineQuoteColor: Color,
  override val postQuoteColor: Color,
  override val postLinkColor: Color,
  override val postSpoilerColor: Color,
  override val postSpoilerRevealTextColor: Color,
  override val dividerColor: Color,
  override val scrollbarTrackColor: Color,
  override val scrollbarThumbColorNormal: Color,
  override val scrollbarThumbColorDragged: Color,
  override val bookmarkCounterHasRepliesColor: Color,
  override val bookmarkCounterNormalColor: Color
) : ChanTheme() {

  override fun fullCopy(): ChanTheme {
    return copy()
  }

}