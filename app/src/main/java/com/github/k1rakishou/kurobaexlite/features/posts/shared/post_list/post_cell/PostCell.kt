package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.res.ResourcesCompat
import com.github.k1rakishou.composecustomtextselection.lib.ConfigurableTextToolbar
import com.github.k1rakishou.composecustomtextselection.lib.SelectableTextContainer
import com.github.k1rakishou.composecustomtextselection.lib.SelectionToolbarMenu
import com.github.k1rakishou.composecustomtextselection.lib.rememberSelectionState
import com.github.k1rakishou.composecustomtextselection.lib.textSelectionAfterDoubleTapOrTapWithLongTap
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListSelectionState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme

@Composable
fun PostCell(
  postViewMode: PostViewMode = PostViewMode.List,
  isCatalogMode: Boolean,
  textSelectionEnabled: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  detectLinkableClicks: Boolean,
  postCellData: PostCellData,
  cellsPadding: PaddingValues,
  postListSelectionState: PostListSelectionState,
  postBlinkAnimationState: PostBlinkAnimationState,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  onPostBind: (PostCellData) -> Unit,
  onPostUnbind: (PostCellData) -> Unit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  onGoToPostClicked: ((PostCellData) -> Unit)?,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
) {
  val catalogScreenViewModel = koinRememberViewModel<CatalogScreenViewModel>()
  val threadScreenViewModel = koinRememberViewModel<ThreadScreenViewModel>()

  val postsFullyParsedOnce by if (isCatalogMode) {
    catalogScreenViewModel.postsFullyParsedOnceFlow.collectAsState(initial = false)
  } else {
    threadScreenViewModel.postsFullyParsedOnceFlow.collectAsState(initial = false)
  }

  DisposableEffect(
    key1 = postCellData,
    key2 = postsFullyParsedOnce,
    effect = {
      if (!postsFullyParsedOnce) {
        return@DisposableEffect onDispose { /**no-op*/ }
      }

      onPostBind(postCellData)
      onDispose { onPostUnbind(postCellData) }
    }
  )

  when (postViewMode) {
    PostViewMode.List -> {
      PostCellListMode(
        chanDescriptor = chanDescriptor,
        currentlyOpenedThread = currentlyOpenedThread,
        postCellData = postCellData,
        cellsPadding = cellsPadding,
        postListSelectionState = postListSelectionState,
        postBlinkAnimationState = postBlinkAnimationState,
        onPostImageClicked = onPostImageClicked,
        onPostImageLongClicked = onPostImageLongClicked,
        reparsePostSubject = reparsePostSubject,
        textSelectionEnabled = textSelectionEnabled,
        detectLinkableClicks = detectLinkableClicks,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onTextSelectionModeChanged = onTextSelectionModeChanged,
        onPostRepliesClicked = onPostRepliesClicked,
        onGoToPostClicked = onGoToPostClicked
      )
    }
    PostViewMode.Grid -> {
      PostCellGridMode(
        staggeredGridMode = false,
        chanDescriptor = chanDescriptor,
        currentlyOpenedThread = currentlyOpenedThread,
        postCellData = postCellData,
        cellsPadding = cellsPadding,
        postListSelectionState = postListSelectionState,
        postBlinkAnimationState = postBlinkAnimationState,
        onPostImageClicked = onPostImageClicked,
        onPostImageLongClicked = onPostImageLongClicked,
        textSelectionEnabled = textSelectionEnabled,
        detectLinkableClicks = detectLinkableClicks,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onTextSelectionModeChanged = onTextSelectionModeChanged,
        onPostRepliesClicked = onPostRepliesClicked
      )
    }
  }
}


@Composable
internal fun PostCellCommentSelectionWrapper(
  textSelectionEnabled: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  content: @Composable (textModifier: Modifier, onTextLayout: (TextLayoutResult) -> Unit) -> Unit
) {
  if (!textSelectionEnabled) {
    content(
      textModifier = Modifier,
      onTextLayout = {}
    )

    return
  }

  val view = LocalView.current
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current
  val selectionState = rememberSelectionState()

  val onCopySelectedTextUpdated by rememberUpdatedState(newValue = onCopySelectedText)
  val onQuoteSelectedTextUpdated by rememberUpdatedState(newValue = onQuoteSelectedText)

  val configurableTextToolbar = remember {
    val resources = context.resources
    val theme = context.theme

    var id = 1
    var order = 0

    val selectionToolbarMenu = SelectionToolbarMenu(
      items = listOf(
        SelectionToolbarMenu.Item(
          id = id++,
          order = order++,
          text = resources.getString(R.string.copy),
          icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_content_copy_24, theme),
          callback = { selectedText -> onCopySelectedTextUpdated.invoke(selectedText.text) }
        ),
        SelectionToolbarMenu.Item(
          id = id++,
          order = order++,
          text = resources.getString(R.string.quote),
          icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_format_quote_24, theme),
          callback = { selectedText -> onQuoteSelectedTextUpdated.invoke(true, selectedText.text) }
        ),
      )
    )

    return@remember ConfigurableTextToolbar(
      view = view,
      selectionToolbarMenu = selectionToolbarMenu
    )
  }

  val textSelectionColors = remember(key1 = chanTheme.accentColor) {
    TextSelectionColors(
      handleColor = chanTheme.accentColor,
      backgroundColor = chanTheme.accentColor.copy(alpha = 0.4f)
    )
  }

  CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
    SelectableTextContainer(
      modifier = Modifier
        .pointerInput(
          key1 = Unit,
          block = { textSelectionAfterDoubleTapOrTapWithLongTap(selectionState) }
        ),
      selectionState = selectionState,
      configurableTextToolbar = configurableTextToolbar,
      onEnteredSelection = { onTextSelectionModeChanged(true) },
      onExitedSelection = { onTextSelectionModeChanged(false) },
      textContent = { modifier, onTextLayout -> content(modifier, onTextLayout) }
    )
  }
}