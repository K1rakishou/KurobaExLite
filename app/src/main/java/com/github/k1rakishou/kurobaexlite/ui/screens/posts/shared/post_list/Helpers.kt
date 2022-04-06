package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list

import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.util.fastAll
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.extractLinkableAnnotationItem
import com.github.k1rakishou.kurobaexlite.model.data.local.SpoilerPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import logcat.logcat

internal suspend fun PointerInputScope.processDragEvents(onPostListDragStateChanged: (Boolean) -> Unit) {
  forEachGesture {
    awaitPointerEventScope {
      val down = awaitPointerEvent(pass = PointerEventPass.Initial)
      if (down.type != PointerEventType.Press) {
        return@awaitPointerEventScope
      }

      onPostListDragStateChanged(true)

      try {
        while (true) {
          val up = awaitPointerEvent(pass = PointerEventPass.Initial)
          if (up.changes.fastAll { it.changedToUp() }) {
            break
          }

          if (up.type == PointerEventType.Release || up.type == PointerEventType.Exit) {
            break
          }
        }
      } finally {
        onPostListDragStateChanged(false)
      }
    }
  }
}

internal fun processClickedAnnotation(
  postsScreenViewModel: PostScreenViewModel,
  postCellData: PostCellData,
  postComment: AnnotatedString,
  characterOffset: Int,
  onLinkableClicked: (PostCellData, PostCommentParser.TextPartSpan.Linkable) -> Unit,
) {
  val parsedPostDataContext = postCellData.parsedPostData?.parsedPostDataContext
    ?: return
  val offset = findFirstNonNewLineCharReversed(characterOffset, postComment)
    ?: return

  val clickedAnnotations = postComment.getStringAnnotations(offset, offset).asReversed()

  for ((index, clickedAnnotation) in clickedAnnotations.withIndex()) {
    when (clickedAnnotation.tag) {
      PostCommentApplier.ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG -> {
        if (!parsedPostDataContext.revealFullPostComment) {
          postsScreenViewModel.reparsePost(
            postCellData = postCellData,
            parsedPostDataContext = parsedPostDataContext.copy(revealFullPostComment = true)
          )
        }

        break
      }
      PostCommentApplier.ANNOTATION_POST_LINKABLE -> {
        val text = postComment.text.substring(clickedAnnotation.start, clickedAnnotation.end)
        val linkable = clickedAnnotation.extractLinkableAnnotationItem()
        logcat(tag = "processClickedAnnotation") {
          "Clicked '${text}' with linkable: ${linkable}"
        }

        if (linkable != null) {
          onLinkableClicked(postCellData, linkable)
        }

        break
      }
      PostCommentApplier.ANNOTATION_POST_SPOILER_TEXT -> {
        logcat(tag = "processClickedAnnotation") {
          "Clicked spoiler text, start=${clickedAnnotation.start}, end=${clickedAnnotation.end}"
        }

        val textSpoilerOpenedPositionSet = parsedPostDataContext.textSpoilerOpenedPositionSet.toMutableSet()
        val spoilerPosition = SpoilerPosition(clickedAnnotation.start, clickedAnnotation.end)
        val isCurrentSpoilerOpened = textSpoilerOpenedPositionSet.contains(spoilerPosition)

        if (isCurrentSpoilerOpened) {
          if (index < clickedAnnotations.size) {
            // If the current spoiler is opened and there are linkables under the spoiler then
            // switch to processing those linkables. This will make it impossible to close the spoiler
            // but this is better than being unable to click spoilered links.
            val hasLinkablesUnderSpoiler = clickedAnnotations.slice(index until clickedAnnotations.size)
              .any { annotation -> annotation.tag == PostCommentApplier.ANNOTATION_POST_LINKABLE }

            if (hasLinkablesUnderSpoiler) {
              continue
            }

            // fallthrough
          }

          textSpoilerOpenedPositionSet.remove(spoilerPosition)
        } else {
          textSpoilerOpenedPositionSet.add(spoilerPosition)
        }

        postsScreenViewModel.reparsePost(
          postCellData = postCellData,
          parsedPostDataContext = parsedPostDataContext
            .copy(textSpoilerOpenedPositionSet = textSpoilerOpenedPositionSet)
        )

        break
      }
    }
  }
}

internal fun createClickableTextColorMap(chanTheme: ChanTheme): Map<String, Color> {
  val postLinkColor = run {
    val resultColor = if (ThemeEngine.isDarkColor(chanTheme.postLinkColorCompose)) {
      ThemeEngine.manipulateColor(chanTheme.postLinkColorCompose, 1.2f)
    } else {
      ThemeEngine.manipulateColor(chanTheme.postLinkColorCompose, 0.8f)
    }

    return@run resultColor.copy(alpha = .4f)
  }

  return mapOf(
    PostCommentApplier.ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG to postLinkColor,
    PostCommentApplier.ANNOTATION_POST_LINKABLE to postLinkColor,
  )
}

internal fun detectClickedAnnotations(
  pos: Offset,
  layoutResult: TextLayoutResult?,
  text: AnnotatedString
): AnnotatedString.Range<String>? {
  val result = layoutResult
    ?: return null
  val offset = findFirstNonNewLineCharReversed(result.getOffsetForPosition(pos), text)
    ?: return null
  val clickedAnnotations = text.getStringAnnotations(offset, offset)

  // Process annotations in reversed order. This way if we have a link inside of a spoiler, when it's
  // clicked, we will first remove the spoiler and then the link will become clickable.
  for (clickedAnnotation in clickedAnnotations.asReversed()) {
    if (clickedAnnotation.tag in PostCommentApplier.ALL_TAGS) {
      return clickedAnnotation
    }
  }

  return null
}

// AnnotatedString.getStringAnnotations() fails (returns no annotations) if the character
// specified by offset is a new line symbol. So we need to find the first non-newline character
// going backwards.
private fun findFirstNonNewLineCharReversed(
  inputOffset: Int,
  text: AnnotatedString
): Int? {
  var offset = inputOffset

  while (true) {
    val ch = text.getOrNull(offset)
      ?: return null

    if (ch != '\n') {
      break
    }

    --offset
  }

  return offset
}