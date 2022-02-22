package com.github.k1rakishou.kurobaexlite.managers

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R

class UiInfoManager(
  private val appContext: Context
) {
  private val resources by lazy { appContext.resources }

  private val _lastTouchPosition = Point(0, 0)
  val lastTouchPosition: Point
    get() = Point(_lastTouchPosition.x, _lastTouchPosition.y)

  val isTablet by lazy { resources.getBoolean(R.bool.isTablet) }

  private var _maxParentWidth: Int = 0
  val maxParentWidth: Int
    get() = _maxParentWidth
  private var _maxParentHeight: Int = 0
  val maxParentHeight: Int
    get() = _maxParentHeight

  val floatingMenuItemTitleSize = mutableStateOf(14.sp)
  val floatingMenuItemSubTitleSize = mutableStateOf(12.sp)

  val isPortraitOrientation: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  val composeDensity by lazy {
    Density(
      appContext.resources.displayMetrics.density,
      appContext.resources.configuration.fontScale
    )
  }

  fun updateMaxParentSize(availableWidth: Int, availableHeight: Int) {
    _maxParentWidth = availableWidth
    _maxParentHeight = availableHeight
  }

  fun mainUiLayoutMode(configuration: Configuration): MainUiLayoutMode {
    val orientation = configuration.orientation
    if (isTablet) {
      return MainUiLayoutMode.Split
    }

    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return MainUiLayoutMode.Portrait
    }

    return MainUiLayoutMode.Split
  }

  fun setLastTouchPosition(x: Float, y: Float) {
    _lastTouchPosition.set(x.toInt(), y.toInt())
  }

}

enum class MainUiLayoutMode {
  Portrait,
  Split
}