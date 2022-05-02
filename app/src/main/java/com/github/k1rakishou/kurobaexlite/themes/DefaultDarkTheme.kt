package com.github.k1rakishou.kurobaexlite.themes

import android.graphics.Color
import androidx.compose.runtime.Stable

@Stable
data class DefaultDarkTheme(
  override val name: String = "Kuroneko",
  override val isLightTheme: Boolean = false,
  override val lightStatusBar: Boolean = true,
  override val lightNavBar: Boolean = true,
  override val accentColor: Int = Color.parseColor("#e0224e"),
  override val primaryColor: Int = Color.parseColor("#171717"),
  override val backColor: Int = Color.parseColor("#212121"),
  override val backColorSecondary: Int = Color.parseColor("#171717"),
  override val selectedOnBackColor: Int = Color.parseColor("#474747"),
  override val highlighterColor: Int = Color.parseColor("#947383"),
  override val errorColor: Int = Color.parseColor("#ff4444"),
  override val textColorPrimary: Int = Color.parseColor("#aeaed6"),
  override val textColorSecondary: Int = Color.parseColor("#8c8ca1"),
  override val textColorHint: Int = Color.parseColor("#7b7b85"),
  override val postSavedReplyColor: Int = Color.parseColor("#753ecf"),
  override val postSubjectColor: Int = Color.parseColor("#d5a6bd"),
  override val postDetailsColor: Int = textColorHint,
  override val postNameColor: Int = Color.parseColor("#996878"),
  override val postInlineQuoteColor: Int = Color.parseColor("#794e94"),
  override val postQuoteColor: Int = Color.parseColor("#ab4d63"),
  override val postHighlightQuoteColor: Int = Color.parseColor("#612c38"),
  override val postLinkColor: Int = Color.parseColor("#ab4d7e"),
  override val postSpoilerColor: Int = Color.parseColor("#000000"),
  override val postSpoilerRevealTextColor: Int = Color.parseColor("#ffffff"),
  override val postUnseenLabelColor: Int = Color.parseColor("#bf3232"),
  override val dividerColor: Int = Color.parseColor("#1effffff"),
  override val scrollbarTrackColor: Int = Color.parseColor("#bababa"),
  override val scrollbarThumbColorNormal: Int = Color.parseColor("#262626"),
  override val scrollbarThumbColorDragged: Int = accentColor,
) : ChanTheme() {

  override fun fullCopy(): ChanTheme {
    return copy()
  }

}