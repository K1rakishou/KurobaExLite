package com.github.k1rakishou.kurobaexlite.managers

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UiInfoManager(
  private val appContext: Context,
  private val appSettings: AppSettings,
  private val coroutineScope: CoroutineScope
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

  val isPortraitOrientation: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  val composeDensity by lazy {
    Density(
      appContext.resources.displayMetrics.density,
      appContext.resources.configuration.fontScale
    )
  }

  private val _floatingMenuItemTitleSizeSp = MutableStateFlow(0.sp)
  val floatingMenuItemTitleSizeSp: StateFlow<TextUnit>
    get() = _floatingMenuItemTitleSizeSp.asStateFlow()
  private val _floatingMenuItemSubTitleSizeSp = MutableStateFlow(0.sp)
  val floatingMenuItemSubTitleSizeSp: StateFlow<TextUnit>
    get() = _floatingMenuItemSubTitleSizeSp.asStateFlow()
  private val _postCellCommentTextSizeSp = MutableStateFlow(0.sp)
  val postCellCommentTextSizeSp: StateFlow<TextUnit>
    get() = _postCellCommentTextSizeSp.asStateFlow()
  private val _postCellSubjectTextSizeSp = MutableStateFlow(0.sp)
  val postCellSubjectTextSizeSp: StateFlow<TextUnit>
    get() = _postCellSubjectTextSizeSp.asStateFlow()
  private val _homeScreenLayoutType = MutableStateFlow(LayoutType.Auto)
  val homeScreenLayoutType: StateFlow<LayoutType>
    get() = _homeScreenLayoutType.asStateFlow()
  private val _bookmarksScreenOnLeftSide = MutableStateFlow(false)
  val bookmarksScreenOnLeftSide: StateFlow<Boolean>
    get() = _bookmarksScreenOnLeftSide.asStateFlow()

  suspend fun init() {
    _floatingMenuItemTitleSizeSp.value = appSettings.floatingMenuItemTitleSizeSp.read().sp
    _floatingMenuItemSubTitleSizeSp.value = appSettings.floatingMenuItemSubTitleSizeSp.read().sp
    _postCellCommentTextSizeSp.value = appSettings.postCellCommentTextSizeSp.read().sp
    _postCellSubjectTextSizeSp.value = appSettings.postCellSubjectTextSizeSp.read().sp
    _homeScreenLayoutType.value = appSettings.layoutType.read()
    _bookmarksScreenOnLeftSide.value = appSettings.bookmarksScreenOnLeftSide.read()

    coroutineScope.launch {
      appSettings.floatingMenuItemTitleSizeSp.valueFlow
        .collectLatest { value -> _floatingMenuItemTitleSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.floatingMenuItemSubTitleSizeSp.valueFlow
        .collectLatest { value -> _floatingMenuItemSubTitleSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.postCellCommentTextSizeSp.valueFlow
        .collectLatest { value -> _postCellCommentTextSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.postCellSubjectTextSizeSp.valueFlow
        .collectLatest { value -> _postCellSubjectTextSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.layoutType.valueFlow
        .collectLatest { value -> _homeScreenLayoutType.value = value }
    }

    coroutineScope.launch {
      appSettings.bookmarksScreenOnLeftSide.valueFlow
        .collectLatest { value -> _bookmarksScreenOnLeftSide.value = value }
    }
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