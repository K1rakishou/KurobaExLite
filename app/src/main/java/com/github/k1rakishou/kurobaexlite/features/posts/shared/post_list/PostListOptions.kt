package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.TextUnit
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

@Immutable
data class PostListOptions(
  val isCatalogMode: Boolean,
  val isInPopup: Boolean,
  val openedFromScreenKey: ScreenKey,
  val pullToRefreshEnabled: Boolean,
  val detectLinkableClicks: Boolean,
  val mainUiLayoutMode: MainUiLayoutMode,
  val contentPadding: PaddingValues,
  val postCellCommentTextSizeSp: TextUnit,
  val postCellSubjectTextSizeSp: TextUnit,
  val orientation: Int
)