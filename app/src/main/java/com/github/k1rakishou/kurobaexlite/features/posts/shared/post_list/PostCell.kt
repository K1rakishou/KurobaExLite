package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberShimmerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun PostCell(
  isCatalogMode: Boolean,
  chanDescriptor: ChanDescriptor,
  detectLinkableClicks: Boolean,
  postCellCommentTextSizeSp: TextUnit,
  postCellSubjectTextSizeSp: TextUnit,
  postCellData: PostCellData,
  onSelectionModeChanged: (Boolean) -> Unit,
  onPostBind: (PostCellData) -> Unit,
  onPostUnbind: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData, Rect) -> Unit,
  reparsePostSubject: suspend (PostCellData) -> AnnotatedString?
) {
  val postComment = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostComment }
  val postSubject = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostSubject }
  val postFooterText = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.postFooterText }

  DisposableEffect(
    key1 = postCellData,
    effect = {
      onPostBind(postCellData)
      onDispose { onPostUnbind(postCellData) }
    })

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(vertical = 8.dp)
  ) {
    PostCellTitle(
      chanDescriptor = chanDescriptor,
      postCellData = postCellData,
      postSubject = postSubject,
      postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
      onPostImageClicked = onPostImageClicked,
      reparsePostSubject = reparsePostSubject
    )

    Spacer(modifier = Modifier.height(4.dp))

    PostCellComment(
      postCellData = postCellData,
      postComment = postComment,
      isCatalogMode = isCatalogMode,
      detectLinkableClicks = detectLinkableClicks,
      postCellCommentTextSizeSp = postCellCommentTextSizeSp,
      onSelectionModeChanged = onSelectionModeChanged,
      onPostCellCommentClicked = onPostCellCommentClicked
    )

    PostCellFooter(
      postCellData = postCellData,
      postFooterText = postFooterText,
      postCellCommentTextSizeSp = postCellCommentTextSizeSp,
      onPostRepliesClicked = onPostRepliesClicked
    )
  }
}

@Composable
private fun PostCellTitle(
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postSubject: AnnotatedString?,
  postCellSubjectTextSizeSp: TextUnit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData, Rect) -> Unit,
  reparsePostSubject: suspend (PostCellData) -> AnnotatedString?
) {
  val chanTheme = LocalChanTheme.current

  Row(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (postCellData.images.isNotNullNorEmpty()) {
      val postImage = postCellData.images.first()
      var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }

      Box(
        modifier = Modifier
          .wrapContentSize()
          .onGloballyPositioned { layoutCoordinates ->
            boundsInWindowMut = layoutCoordinates.boundsInWindow()
          }
          .kurobaClickable(
            onClick = {
              val boundsInWindow = boundsInWindowMut
              if (boundsInWindow == null) {
                return@kurobaClickable
              }

              onPostImageClicked(chanDescriptor, postImage, boundsInWindow)
            }
          )
      ) {
        ImageThumbnail(
          modifier = Modifier.size(70.dp),
          postImage = postImage,
        )
      }

      Spacer(modifier = Modifier.width(4.dp))
    }

    if (postSubject == null) {
      Shimmer(
        modifier = Modifier
          .weight(1f)
          .height(42.dp),
        shimmerState = rememberShimmerState(bgColor = chanTheme.backColorCompose)
      )
    } else {
      var actualPostSubject by remember { mutableStateOf(postSubject) }

      LaunchedEffect(
        key1 = postCellData,
        block = {
          val initialTime = postCellData.timeMs
            ?: return@LaunchedEffect

          while (isActive) {
            val now = System.currentTimeMillis()
            val delay = if (now - initialTime <= 60_000L) 5000L else 60_000L

            delay(delay)

            val newPostSubject = reparsePostSubject(postCellData)
              ?: continue

            actualPostSubject = newPostSubject
          }
        })

      Text(
        modifier = Modifier.weight(1f),
        text = actualPostSubject,
        fontSize = postCellSubjectTextSizeSp
      )
    }
  }
}

@Composable
private fun PostCellComment(
  postCellData: PostCellData,
  postComment: AnnotatedString?,
  isCatalogMode: Boolean,
  detectLinkableClicks: Boolean,
  postCellCommentTextSizeSp: TextUnit,
  onSelectionModeChanged: (Boolean) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val clickedTextBackgroundColorMap = remember(key1 = chanTheme) { createClickableTextColorMap(chanTheme) }

  if (postComment.isNotNullNorBlank()) {
    PostCellCommentSelectionWrapper(
      isCatalogMode = isCatalogMode,
      onSelectionModeChanged = onSelectionModeChanged
    ) {
      KurobaComposeClickableText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fontSize = postCellCommentTextSizeSp,
        text = postComment,
        isTextClickable = detectLinkableClicks,
        annotationBgColors = clickedTextBackgroundColorMap,
        detectClickedAnnotations = { offset, textLayoutResult, text ->
          return@KurobaComposeClickableText detectClickedAnnotations(offset, textLayoutResult, text)
        },
        onTextAnnotationClicked = { text, offset -> onPostCellCommentClicked(postCellData, text, offset) }
      )
    }
  } else if (postComment == null) {
    Shimmer(
      modifier = Modifier
        .fillMaxWidth()
        .height(80.dp),
      shimmerState = rememberShimmerState(bgColor = chanTheme.backColorCompose)
    )
  }
}

/**
 * This wrapper changes the default long tap to start text selection gesture to tap + long tap one.
 * This is needed because the regular long tap is used to show the content menu.
 * */
@Composable
private fun PostCellCommentSelectionWrapper(
  isCatalogMode: Boolean,
  onSelectionModeChanged: (Boolean) -> Unit,
  content: @Composable () -> Unit
) {
  var inSelectionMode by remember { mutableStateOf<Boolean?>(null) }
  var tapUpEventTime by remember { mutableStateOf<Long>(0) }

  val gestureDetectorModifier = if (!isCatalogMode) {
    Modifier
      .pointerInput(
        key1 = Unit,
        block = {
          forEachGesture {
            val down = awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }

            val downTime = SystemClock.elapsedRealtime()
            if (downTime - tapUpEventTime < 500L) {
              return@forEachGesture
            }

            val up = awaitPointerEventScope { waitForUpOrCancellation() }
            val upTime = SystemClock.elapsedRealtime()

            if (inSelectionMode == true) {
              onSelectionModeChanged(false)
              inSelectionMode = false
              tapUpEventTime = 0
              return@forEachGesture
            }

            if (up == null || (upTime - downTime) > 150) {
              return@forEachGesture
            }

            down.consumeAllChanges()
            up.consumeAllChanges()

            onSelectionModeChanged(true)
            inSelectionMode = true

            tapUpEventTime = SystemClock.elapsedRealtime()
          }
        }
      )
  } else {
    Modifier
  }

  Box(
    modifier = gestureDetectorModifier
  ) {
    if (!isCatalogMode && inSelectionMode == true) {
      SelectionContainer {
        content()
      }
    } else {
      content()
    }
  }
}

@Composable
private fun PostCellFooter(
  postCellData: PostCellData,
  postFooterText: AnnotatedString?,
  postCellCommentTextSizeSp: TextUnit,
  onPostRepliesClicked: (PostCellData) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  if (postFooterText.isNotNullNorEmpty()) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(onClick = { onPostRepliesClicked(postCellData) })
        .padding(vertical = 4.dp),
      color = chanTheme.textColorSecondaryCompose,
      fontSize = postCellCommentTextSizeSp,
      text = postFooterText
    )
  }
}