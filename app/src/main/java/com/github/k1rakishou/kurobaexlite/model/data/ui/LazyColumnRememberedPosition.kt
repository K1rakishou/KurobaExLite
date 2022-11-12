package com.github.k1rakishou.kurobaexlite.model.data.ui

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

data class LazyColumnRememberedPosition(
  var orientation: Int,
  var index: Int = 0,
  var offset: Int = 0
) {

  fun toLazyColumnRememberedPositionEvent(): LazyColumnRememberedPositionEvent {
    return LazyColumnRememberedPositionEvent(
      orientation = orientation,
      index = index,
      offset = offset
    )
  }

}

data class LazyColumnRememberedPositionEvent(
  val orientation: Int,
  val index: Int = 0,
  val offset: Int = 0,
  val blinkPostDescriptor: PostDescriptor? = null
)