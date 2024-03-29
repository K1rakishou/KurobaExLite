package com.github.k1rakishou.kurobaexlite.ui.themes.def

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.ui.themes.ChanTheme

@Stable
data class Kuroneko(
  override val name: String = "Kuroneko",
  override val isLightTheme: Boolean = false,
  override val lightStatusBar: Boolean = true,
  override val lightNavBar: Boolean = true,
  override val accentColor: Color = Color(0xffe0224e),
  override val gradientTopColor: Color = Color(0x088252d1),
  override val gradientBottomColor: Color = Color(0x08e01645),
  override val behindGradientColor: Color = Color(0xff303030),
  override val backColor: Color = Color(0xff282828),
  override val backColorSecondary: Color = Color(0xff202020),
  override val selectedOnBackColor: Color = Color(0xff855a5a),
  override val highlightedOnBackColor: Color = Color(0xff947383),
  override val errorColor: Color = Color(0xffff4444),
  override val textColorPrimary: Color = Color(0xffaeaed6),
  override val textColorSecondary: Color = Color(0xff8c8ca1),
  override val textColorHint: Color = Color(0xff7b7b85),
  override val postSavedReplyColor: Color = Color(0xff753ecf),
  override val postSubjectColor: Color = Color(0xffd5a6bd),
  override val postDetailsColor: Color = textColorHint,
  override val postNameColor: Color = Color(0xff996878),
  override val postInlineQuoteColor: Color = Color(0xff794e94),
  override val postQuoteColor: Color = Color(0xffab4d63),
  override val postLinkColor: Color = Color(0xffab4d7e),
  override val postSpoilerColor: Color = Color(0xff000000),
  override val postSpoilerRevealTextColor: Color = Color(0xffffffff),
  override val dividerColor: Color = Color(0xffffffff),
  override val scrollbarTrackColor: Color = Color(0xffbababa),
  override val scrollbarThumbColorNormal: Color = Color(0xff262626),
  override val scrollbarThumbColorDragged: Color = accentColor,
  override val bookmarkCounterHasRepliesColor: Color = Color(0xffff4444),
  override val bookmarkCounterNormalColor: Color = Color(0xff33B5E5),
) : ChanTheme() {

  override fun fullCopy(): ChanTheme {
    return copy()
  }

}