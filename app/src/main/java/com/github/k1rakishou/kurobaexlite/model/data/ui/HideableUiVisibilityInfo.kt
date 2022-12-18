package com.github.k1rakishou.kurobaexlite.model.data.ui

import android.os.Parcel
import android.os.Parcelable
import com.github.k1rakishou.kurobaexlite.helpers.util.readBooleanKt
import com.github.k1rakishou.kurobaexlite.helpers.util.writeBooleanKt
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HideableUiVisibilityInfo() : Parcelable {
  private val _contentListScrollState = MutableStateFlow<Float>(1f)
  val contentListScrollState: StateFlow<Float>
    get() = _contentListScrollState.asStateFlow()

  private val _contentListTouchingTopOrBottomState = MutableStateFlow<Boolean>(false)
  val contentListTouchingTopOrBottomState: StateFlow<Boolean>
    get() = _contentListTouchingTopOrBottomState.asStateFlow()

  private val _contentListTouchingState = MutableStateFlow(false)
  val contentListTouchingState: StateFlow<Boolean>
    get() = _contentListTouchingState.asStateFlow()

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

  private val _isInPostSelectionMode = MutableStateFlow(false)
  val isInPostSelectionMode: StateFlow<Boolean>
    get() = _isInPostSelectionMode.asStateFlow()

  constructor(parcel: Parcel) : this() {
    _contentListScrollState.value = parcel.readFloat()
    _contentListTouchingTopOrBottomState.value = parcel.readBooleanKt()
    _hasLoadError.value = parcel.readBooleanKt()
    _replyLayoutOpened.value = parcel.readBooleanKt()
    _isInPostSelectionMode.value = parcel.readBooleanKt()
  }

  fun update(
    contentListScrollState: Float? = null,
    contentListTouchingTopOrBottomState: Boolean? = null,
    contentListTouchingState: Boolean? = null,
    fastScrollerDragState: Boolean? = null,
    hasLoadError: Boolean? = null,
    replyLayoutOpened: Boolean? = null,
    isInPostSelectionMode: Boolean? = null,
    childScreenSearchInfo: ChildScreenSearchInfo? = null
  ) {
    contentListScrollState?.let { _contentListScrollState.value = it }
    contentListTouchingTopOrBottomState?.let { _contentListTouchingTopOrBottomState.value = it }
    contentListTouchingState?.let { _contentListTouchingState.value = it }
    fastScrollerDragState?.let { _fastScrollerDragState.value = it }
    hasLoadError?.let { _hasLoadError.value = it }
    replyLayoutOpened?.let { _replyLayoutOpened.value = it }
    isInPostSelectionMode?.let { _isInPostSelectionMode.value = it }

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
    parcel.writeFloat(contentListScrollState.value)
    parcel.writeBooleanKt(_contentListTouchingTopOrBottomState.value)
    parcel.writeBooleanKt(_hasLoadError.value)
    parcel.writeBooleanKt(_replyLayoutOpened.value)
    parcel.writeBooleanKt(_isInPostSelectionMode.value)
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