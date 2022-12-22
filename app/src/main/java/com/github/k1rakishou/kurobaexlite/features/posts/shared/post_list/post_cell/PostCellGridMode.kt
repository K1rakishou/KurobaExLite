package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import androidx.compose.animation.Animatable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostImageThumbnail
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListSelectionState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.createClickableTextColorMap
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.detectClickedAnnotations
import com.github.k1rakishou.kurobaexlite.helpers.util.ensureSingleMeasurable
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PostCellGridMode(
  staggeredGridMode: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  postCellData: PostCellData,
  cellsPadding: PaddingValues,
  postListSelectionState: PostListSelectionState,
  postBlinkAnimationState: PostBlinkAnimationState,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val isCatalogMode = chanDescriptor is CatalogDescriptor

  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val catalogGridModeColumnCount by globalUiInfoManager.catalogGridModeColumnCount.collectAsState()

  val startPadding = remember(key1 = cellsPadding) { cellsPadding.calculateStartPadding(LayoutDirection.Ltr) }
  val endPadding = remember(key1 = cellsPadding) { cellsPadding.calculateEndPadding(LayoutDirection.Ltr) }
  val bottomPadding = remember(key1 = cellsPadding) { cellsPadding.calculateBottomPadding().coerceAtLeast(4.dp) }
  val highlightColorWithAlpha = remember(key1 = chanTheme.highlightedOnBackColor) { chanTheme.highlightedOnBackColor.copy(alpha = 0.3f) }
  val selectColorWithAlpha = remember(key1 = chanTheme.selectedOnBackColor) { chanTheme.selectedOnBackColor.copy(alpha = 0.5f) }

  val isInPostSelectionMode by postListSelectionState.isInSelectionMode
  val isPostSelected = if (isInPostSelectionMode) {
    postListSelectionState.isPostSelected(postCellData.postDescriptor)
  } else {
    false
  }

  val postCellBackgroundColor = remember(
    isCatalogMode,
    currentlyOpenedThread,
    postCellData.postDescriptor,
    isInPostSelectionMode,
    isPostSelected,
    chanTheme,
    highlightColorWithAlpha
  ) {
    if (isInPostSelectionMode) {
      if (isPostSelected) {
        return@remember selectColorWithAlpha
      }

      return@remember chanTheme.backColorSecondary
    }

    if (isCatalogMode && currentlyOpenedThread == postCellData.postDescriptor.threadDescriptor) {
      return@remember highlightColorWithAlpha
    }

    return@remember chanTheme.backColorSecondary
  }

  val postCellBackgroundColorAnimatable = remember(postCellBackgroundColor) { Animatable(initialValue = postCellBackgroundColor) }
  val postCellBackgroundColorAnimatableProvider = remember(postCellBackgroundColor) { { postCellBackgroundColorAnimatable } }

  BlinkAnimation(
    postCellDefaultBgColor = postCellBackgroundColor,
    postCellBlinkBgColor = highlightColorWithAlpha,
    postDescriptor = postCellData.postDescriptor,
    postBlinkAnimationState = postBlinkAnimationState,
    postCellBackgroundColorAnimatableProvider = postCellBackgroundColorAnimatableProvider
  )

  val ratio = when {
    catalogGridModeColumnCount <= 1 -> 9f / 12f
    catalogGridModeColumnCount == 2 -> 9f / 16f
    else -> 9f / 18f
  }

  KurobaComposeCard(
    modifier = Modifier.padding(4.dp),
    backgroundColor = postCellBackgroundColorAnimatable.value
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .wrapContentHeight()
        .aspectRatio(ratio = ratio)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
      ) {
        PostCellGridModeLayout(
          title = {
            Column {
              PostCellTitle(
                startPadding = startPadding,
                endPadding = endPadding,
                chanDescriptor = chanDescriptor,
                postCellData = postCellData,
                postListSelectionState = postListSelectionState,
                onPostImageClicked = onPostImageClicked,
                onPostImageLongClicked = onPostImageLongClicked
              )
            }
          },
          comment = {
            PostCellComment(
              startPadding = startPadding,
              endPadding = endPadding,
              postCellData = postCellData,
              textSelectionEnabled = textSelectionEnabled,
              detectLinkableClicks = detectLinkableClicks,
              onCopySelectedText = onCopySelectedText,
              onQuoteSelectedText = onQuoteSelectedText,
              onPostCellCommentClicked = onPostCellCommentClicked,
              onPostCellCommentLongClicked = onPostCellCommentLongClicked,
              onTextSelectionModeChanged = onTextSelectionModeChanged
            )
          },
          footer = {
            PostCellFooter(
              startPadding = startPadding,
              endPadding = endPadding,
              bottomPadding = bottomPadding,
              postCellData = postCellData,
              onPostRepliesClicked = onPostRepliesClicked
            )
          }
        )
      }
    }
  }
}

@Composable
private fun PostCellGridModeLayout(
  title: @Composable () -> Unit,
  comment: @Composable () -> Unit,
  footer: @Composable () -> Unit,
) {
  Layout(
    contents = listOf(title, comment, footer),
    measurePolicy = { measurables, constraints ->
      require(constraints.hasBoundedHeight) {
        "This layout cannot be used as a root of scrollable container because one of the children's " +
          "height depends on the parent's height, it will be infinite in case of parent having infinite height."
      }

      val footerPlaceables = measurables[2].map { measurable -> measurable.measure(constraints) }
      val footerTotalHeight = footerPlaceables.sumOf { it.measuredHeight }

      val titlePlaceable = measurables[0].ensureSingleMeasurable()
        .measure(constraints.copy(maxHeight = constraints.maxHeight - footerTotalHeight))
      val titleTotalHeight = titlePlaceable.measuredHeight

      val availableHeightForComment = constraints.maxHeight - titleTotalHeight - footerTotalHeight
      val commentPlaceables = if (availableHeightForComment > 0) {
        measurables[1]
          .map { measurable -> measurable.measure(constraints.copy(maxHeight = availableHeightForComment)) }
      } else {
        emptyList()
      }

      return@Layout layout(constraints.maxWidth, constraints.maxHeight) {
        var currentY = 0

        titlePlaceable.place(0, currentY)
        currentY += titlePlaceable.measuredHeight

        commentPlaceables.forEach { placeable ->
          placeable.place(0, currentY)
          currentY += placeable.measuredHeight
        }

        footerPlaceables.forEach { placeable ->
          placeable.place(0, currentY)
          currentY += placeable.measuredHeight
        }
      }
    }
  )
}

@Composable
private fun PostCellFooter(
  startPadding: Dp,
  endPadding: Dp,
  bottomPadding: Dp,
  postCellData: PostCellData,
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
      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .kurobaClickable(onClick = { onPostRepliesClicked(postCellData) })
          .padding(start = startPadding, top = 4.dp, end = endPadding, bottom = bottomPadding),
        color = chanTheme.textColorSecondary,
        fontSize = 16.sp,
        text = postFooterText
      )
    } else {
      Spacer(modifier = Modifier.fillMaxWidth(1f))
    }
  }
}


@Composable
private fun PostCellComment(
  startPadding: Dp,
  endPadding: Dp,
  postCellData: PostCellData,
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
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

  if (postComment != null) {
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
          .fillMaxSize()
          .padding(start = startPadding, end = endPadding)
          .then(textModifier),
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
  } else {
    Shimmer(
      modifier = Modifier.fillMaxSize()
    )
  }
}

@Composable
private fun PostCellTitle(
  startPadding: Dp,
  endPadding: Dp,
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postListSelectionState: PostListSelectionState,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
) {
  val postSubject = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostSubject }

  if (postCellData.images.isNotNullNorEmpty()) {
    PostCellThumbnail(
      postCellData = postCellData,
      postListSelectionState = postListSelectionState,
      onPostImageClicked = onPostImageClicked,
      onPostImageLongClicked = onPostImageLongClicked,
      chanDescriptor = chanDescriptor
    )

    Spacer(modifier = Modifier.height(4.dp))
  }

  if (postSubject.isNotNullNorBlank()) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = startPadding, end = endPadding),
      text = postSubject,
      maxLines = 2,
      inlineContent = inlinedContentForPostCell(
        postCellData = postCellData
      )
    )

    Spacer(modifier = Modifier.height(4.dp))
  } else if (postSubject == null) {
    Shimmer(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = startPadding, end = endPadding)
    )
  }
}

@Composable
private fun PostCellThumbnail(
  postCellData: PostCellData,
  postListSelectionState: PostListSelectionState,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  chanDescriptor: ChanDescriptor
) {
  val postImage = postCellData.images!!.first()
  var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }
  val isInSelectionMode by postListSelectionState.isInSelectionMode

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
      onClick = { clickedImage ->
        if (isInSelectionMode) {
          postListSelectionState.toggleSelection(postImage.ownerPostDescriptor)
          return@PostImageThumbnail
        }

        val boundsInWindow = boundsInWindowMut
          ?: return@PostImageThumbnail

        onPostImageClicked(chanDescriptor, clickedImage, boundsInWindow)
      },
      onLongClick = { longClickedImage ->
        if (isInSelectionMode) {
          postListSelectionState.toggleSelection(postImage.ownerPostDescriptor)
          return@PostImageThumbnail
        }

        onPostImageLongClicked(chanDescriptor, longClickedImage)
      }
    )
  }
}