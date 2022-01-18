package com.github.k1rakishou.kurobaexlite.managers

class UiInfoManager {
  private var _maxParentWidth: Int = 0
  val maxParentWidth: Int
    get() = _maxParentWidth
  private var _maxParentHeight: Int = 0
  val maxParentHeight: Int
    get() = _maxParentHeight

  fun updateMaxParentSize(availableWidth: Int, availableHeight: Int) {
    _maxParentWidth = availableWidth
    _maxParentHeight = availableHeight
  }

}