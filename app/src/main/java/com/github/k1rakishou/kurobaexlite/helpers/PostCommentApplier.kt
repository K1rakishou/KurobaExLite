package com.github.k1rakishou.kurobaexlite.helpers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme

class PostCommentApplier {

  fun textPartsToAnnotatedString(
    chanTheme: ChanTheme,
    textParts: List<PostCommentParser.TextPart>
  ): AnnotatedString {
    return buildAnnotatedString {
      for (textPart in textParts) {
        append(textPartToAnnotatedString(chanTheme, textPart))
      }
    }
  }

  private fun textPartToAnnotatedString(
    chanTheme: ChanTheme,
    textPart: PostCommentParser.TextPart
  ): AnnotatedString {
    return buildAnnotatedString {
      append(textPart.text)

      for (span in textPart.spans) {
        var bgColor: Color = Color.Unspecified
        var fgColor: Color = Color.Unspecified

        when (span) {
          is PostCommentParser.TextPartSpan.BgColor -> {
            bgColor = Color(span.color)
          }
          is PostCommentParser.TextPartSpan.FgColor -> {
            fgColor = Color(span.color)
          }
          is PostCommentParser.TextPartSpan.BgColorId -> {
            bgColor = Color(chanTheme.getColorByColorId(span.colorId))
          }
          is PostCommentParser.TextPartSpan.FgColorId -> {
            fgColor = Color(chanTheme.getColorByColorId(span.colorId))
          }
        }

        if (bgColor.isUnspecified && fgColor.isUnspecified) {
          continue
        }

        addStyle(SpanStyle(color = fgColor, background = bgColor), 0, length)
      }
    }
  }

}