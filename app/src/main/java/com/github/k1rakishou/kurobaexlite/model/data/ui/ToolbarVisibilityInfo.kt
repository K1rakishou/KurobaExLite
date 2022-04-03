package com.github.k1rakishou.kurobaexlite.model.data.ui

import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ToolbarVisibilityInfo {
  private val _postListScrollState = MutableStateFlow<Float>(1f)
  val postListScrollState: StateFlow<Float>
    get() = _postListScrollState.asStateFlow()

  private val _postListTouchingTopOrBottomState = MutableStateFlow<Boolean>(false)
  val postListTouchingTopOrBottomState: StateFlow<Boolean>
    get() = _postListTouchingTopOrBottomState.asStateFlow()

  private val _postListDragState = MutableStateFlow(false)
  val postListDragState: StateFlow<Boolean>
    get() = _postListDragState.asStateFlow()

  private val _fastScrollerDragState = MutableStateFlow(false)
  val fastScrollerDragState: StateFlow<Boolean>
    get() = _fastScrollerDragState.asStateFlow()

  private val _childScreensUsingSearch = MutableStateFlow(mutableSetOf<ScreenKey>())
  val childScreensUsingSearch: StateFlow<Set<ScreenKey>>
    get() = _childScreensUsingSearch.asStateFlow()

  fun update(
    postListScrollState: Float? = null,
    postListTouchingTopOrBottomState: Boolean? = null,
    postListDragState: Boolean? = null,
    fastScrollerDragState: Boolean? = null,
    childScreenSearchInfo: ChildScreenSearchInfo? = null
  ) {
    postListScrollState?.let { _postListScrollState.value = it }
    postListTouchingTopOrBottomState?.let { _postListTouchingTopOrBottomState.value = it }
    postListDragState?.let { _postListDragState.value = it }
    fastScrollerDragState?.let { _fastScrollerDragState.value = it }

    childScreenSearchInfo?.let { screenSearchInfo ->
      val prevValue = _childScreensUsingSearch.value.toMutableSet()

      if (screenSearchInfo.usingSearch) {
        prevValue += screenSearchInfo.screenKey
      } else {
        prevValue -= screenSearchInfo.screenKey
      }

      _childScreensUsingSearch.value = prevValue
    }
  }

}