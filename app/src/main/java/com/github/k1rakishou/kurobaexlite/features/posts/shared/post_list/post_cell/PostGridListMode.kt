package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostImageThumbnail
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.createClickableTextColorMap
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.detectClickedAnnotations
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable


@Composable
fun PostCellGridMode(
  staggeredGridMode: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  postCellData: PostCellData,
  cellsPadding: PaddingValues,
  postCellSubjectTextSizeSp: TextUnit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  postCellCommentTextSizeSp: TextUnit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val isCatalogMode = chanDescriptor is CatalogDescriptor

  val resultPaddings = remember(key1 = cellsPadding) {
    return@remember PaddingValues(
      start = cellsPadding.calculateStartPadding(LayoutDirection.Ltr),
      end = cellsPadding.calculateEndPadding(LayoutDirection.Ltr),
      top = cellsPadding.calculateTopPadding(),
      bottom = cellsPadding.calculateBottomPadding()
    )
  }

  val postCellBackgroundColor = remember(
    key1 = isCatalogMode,
    key2 = currentlyOpenedThread,
    key3 = postCellData.postDescriptor
  ) {
    if (isCatalogMode && currentlyOpenedThread == postCellData.postDescriptor.threadDescriptor) {
      chanTheme.highlighterColor.copy(alpha = 0.3f)
    } else {
      chanTheme.backColor
    }
  }

  KurobaComposeCard(
    modifier = Modifier
      .padding(4.dp),
    backgroundColor = postCellBackgroundColor
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .wrapContentHeight()
        .aspectRatio(ratio = 9f / 18f)
        .padding(resultPaddings)
    ) {
      PostCellTitle(
        chanDescriptor = chanDescriptor,
        postCellData = postCellData,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        onPostImageClicked = onPostImageClicked
      )

      Spacer(modifier = Modifier.height(4.dp))

      PostCellComment(
        postCellData = postCellData,
        textSelectionEnabled = textSelectionEnabled,
        detectLinkableClicks = detectLinkableClicks,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onTextSelectionModeChanged = onTextSelectionModeChanged
      )

      Spacer(modifier = Modifier.weight(1f))

      PostCellFooter(
        postCellData = postCellData,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        onPostRepliesClicked = onPostRepliesClicked
      )
    }
  }
}

@Composable
private fun PostCellFooter(
  postCellData: PostCellData,
  postCellCommentTextSizeSp: TextUnit,
  onPostRepliesClicked: (PostCellData) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val postFooterText = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.postFooterText }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (postFooterText.isNotNullNorEmpty()) {
      Text(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight()
          .kurobaClickable(onClick = { onPostRepliesClicked(postCellData) })
          .padding(vertical = 4.dp),
        color = chanTheme.textColorSecondary,
        fontSize = postCellCommentTextSizeSp,
        text = postFooterText
      )
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }
  }

}


@Composable
private fun PostCellComment(
  postCellData: PostCellData,
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
  postCellCommentTextSizeSp: TextUnit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val clickedTextBackgroundColorMap = remember(key1 = chanTheme) { createClickableTextColorMap(chanTheme) }
  val postComment = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostComment }
  var isInSelectionMode by remember { mutableStateOf(false) }

  if (postComment.isNotNullNorBlank()) {
    PostCellCommentSelectionWrapper(
      textSelectionEnabled = textSelectionEnabled,
      onCopySelectedText = onCopySelectedText,
      onQuoteSelectedText = { withText, selectedText -> onQuoteSelectedText(withText, selectedText, postCellData) },
      onTextSelectionModeChanged = { inSelectionMode ->
        isInSelectionMode = inSelectionMode
        onTextSelectionModeChanged(inSelectionMode)
      }
    ) { textModifier, onTextLayout ->
      KurobaComposeClickableText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .then(textModifier),
        fontSize = postCellCommentTextSizeSp,
        text = postComment,
        overflow = TextOverflow.Ellipsis,
        isTextClickable = detectLinkableClicks && !isInSelectionMode,
        annotationBgColors = clickedTextBackgroundColorMap,
        detectClickedAnnotations = { offset, textLayoutResult, text ->
          return@KurobaComposeClickableText detectClickedAnnotations(offset, textLayoutResult, text)
        },
        onTextAnnotationClicked = { text, offset -> onPostCellCommentClicked(postCellData, text, offset) },
        onTextAnnotationLongClicked = { text, offset -> onPostCellCommentLongClicked(postCellData, text, offset) },
        onTextLayout = onTextLayout
      )
    }
  } else if (postComment == null) {
    Shimmer(
      modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
    )
  }
}

@Composable
private fun ColumnScope.PostCellTitle(
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postCellSubjectTextSizeSp: TextUnit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
) {
  val postSubject = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostSubject }

  if (postCellData.images.isNotNullNorEmpty()) {
    PostCellThumbnail(
      images = postCellData.images,
      onPostImageClicked = onPostImageClicked,
      chanDescriptor = chanDescriptor
    )

    Spacer(modifier = Modifier.height(4.dp))
  }

  if (postSubject == null) {
    Shimmer(
      modifier = Modifier
        .weight(1f)
        .height(42.dp)
    )
  } else {
    Text(
      modifier = Modifier.fillMaxWidth(),
      text = postSubject,
      fontSize = postCellSubjectTextSizeSp,
      inlineContent = inlinedContentForPostCell(
        postCellData = postCellData,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp
      )
    )
  }
}

@Composable
private fun PostCellThumbnail(
  images: List<PostCellImageData>,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  chanDescriptor: ChanDescriptor
) {
  val postImage = images.first()
  var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }

  Box(
    modifier = Modifier
      .wrapContentSize()
      .onGloballyPositioned { layoutCoordinates ->
        boundsInWindowMut = layoutCoordinates.boundsInWindow()
      }
  ) {
    PostImageThumbnail(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(ratio = 16f / 13f),
      postImage = postImage,
      contentScale = ContentScale.Crop,
      onClickWithError = { clickedImageResult ->
        val boundsInWindow = boundsInWindowMut
          ?: return@PostImageThumbnail

        onPostImageClicked(chanDescriptor, clickedImageResult, boundsInWindow)
      }
    )
  }
}