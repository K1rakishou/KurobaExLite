package com.github.k1rakishou.kurobaexlite.model.data.ui

import android.os.Parcel
import android.os.Parcelable
import com.github.k1rakishou.kurobaexlite.helpers.readBooleanKt
import com.github.k1rakishou.kurobaexlite.helpers.writeBooleanKt
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HideableUiVisibilityInfo() : Parcelable {
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

  private val _hasLoadError = MutableStateFlow(false)
  val hasLoadError: StateFlow<Boolean>
    get() = _hasLoadError.asStateFlow()

  private val _childScreensUsingSearch = MutableStateFlow(mutableSetOf<ScreenKey>())
  val childScreensUsingSearch: StateFlow<Set<ScreenKey>>
    get() = _childScreensUsingSearch.asStateFlow()

  private val _replyLayoutOpened = MutableStateFlow(false)
  val replyLayoutOpened: StateFlow<Boolean>
    get() = _replyLayoutOpened.asStateFlow()

  constructor(parcel: Parcel) : this() {
    _postListScrollState.value = parcel.readFloat()
    _postListTouchingTopOrBottomState.value = parcel.readBooleanKt()
    _hasLoadError.value = parcel.readBooleanKt()
    _replyLayoutOpened.value = parcel.readBooleanKt()
  }

  fun update(
    postListScrollState: Float? = null,
    postListTouchingTopOrBottomState: Boolean? = null,
    postListDragState: Boolean? = null,
    fastScrollerDragState: Boolean? = null,
    hasLoadError: Boolean? = null,
    replyLayoutOpened: Boolean? = null,
    childScreenSearchInfo: ChildScreenSearchInfo? = null
  ) {
    postListScrollState?.let { _postListScrollState.value = it }
    postListTouchingTopOrBottomState?.let { _postListTouchingTopOrBottomState.value = it }
    postListDragState?.let { _postListDragState.value = it }
    fastScrollerDragState?.let { _fastScrollerDragState.value = it }
    hasLoadError?.let { _hasLoadError.value = it }
    replyLayoutOpened?.let { _replyLayoutOpened.value = it }

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

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeFloat(postListScrollState.value)
    parcel.writeBooleanKt(_postListTouchingTopOrBottomState.value)
    parcel.writeBooleanKt(_hasLoadError.value)
    parcel.writeBooleanKt(_replyLayoutOpened.value)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<HideableUiVisibilityInfo> {
    override fun createFromParcel(parcel: Parcel): HideableUiVisibilityInfo {
      return HideableUiVisibilityInfo(parcel)
    }

    override fun newArray(size: Int): Array<HideableUiVisibilityInfo?> {
      return arrayOfNulls(size)
    }
  }

}