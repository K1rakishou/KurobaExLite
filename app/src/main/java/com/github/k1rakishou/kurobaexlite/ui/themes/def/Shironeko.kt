package com.github.k1rakishou.kurobaexlite.ui.themes.def

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.ui.themes.ChanTheme

@Stable
data class Shironeko(
  override val name: String = "Shironeko",
  override val isLightTheme: Boolean = true,
  override val lightStatusBar: Boolean = false,
  override val lightNavBar: Boolean = false,
  override val accentColor: Color = Color(0xffb01a3e),
  override val gradientTopColor: Color = Color(0x100261c7),
  override val gradientBottomColor: Color = Color(0x106e1180),
  override val behindGradientColor: Color = Color(0xffF0F0F0),
  override val backColor: Color = Color(0xffF4F4F2),
  override val backColorSecondary: Color = Color(0xFFe0e0e0),
  override val selectedOnBackColor: Color = Color(0xff909090),
  override val highlightedOnBackColor: Color = Color(0xff947383),
  override val errorColor: Color = Color(0xffff4444),
  override val textColorPrimary: Color = Color(0xff1b1b4a),
  override val textColorSecondary: Color = Color(0xff42427d),
  override val textColorHint: Color = Color(0xff5b5b82),
  override val postSavedReplyColor: Color = Color(0xff753ecf),
  override val postSubjectColor: Color = Color(0xffd15665),
  override val postDetailsColor: Color = textColorHint,
  override val postNameColor: Color = Color(0xff5E2C51),
  override val postInlineQuoteColor: Color = Color(0xff794e94),
  override val postQuoteColor: Color = Color(0xffab4d63),
  override val postLinkColor: Color = Color(0xffab4d7e),
  override val postSpoilerColor: Color = Color(0xff000000),
  override val postSpoilerRevealTextColor: Color = Color(0xffffffff),
  override val dividerColor: Color = Color(0xff121212),
  override val scrollbarTrackColor: Color = Color(0xffa0a0a0),
  override val scrollbarThumbColorNormal: Color = Color(0xff636363),
  override val scrollbarThumbColorDragged: Color = accentColor,
  override val bookmarkCounterHasRepliesColor: Color = Color(0xffff4444),
  override val bookmarkCounterNormalColor: Color = Color(0xff33B5E5),
) : ChanTheme() {

  override fun fullCopy(): ChanTheme {
    return copy()
  }

}