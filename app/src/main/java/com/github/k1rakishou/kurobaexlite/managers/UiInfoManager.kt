package com.github.k1rakishou.kurobaexlite.managers

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import com.github.k1rakishou.kurobaexlite.model.data.ui.ChildScreenSearchInfo
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.ToolbarVisibilityInfo
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.layout.SplitScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import logcat.logcat

class UiInfoManager(
  private val appContext: Context,
  private val appSettings: AppSettings,
  private val coroutineScope: CoroutineScope
) {
  private val initialized = AtomicBoolean(false)

  private val resources by lazy { appContext.resources }
  private val toolbarHeight by lazy { resources.getDimension(R.dimen.toolbar_height) }
  private val toolbarVisibilityInfoMap = mutableMapOf<ScreenKey, ToolbarVisibilityInfo>()

  private val _lastTouchPosition = Point(0, 0)
  val lastTouchPosition: Point
    get() = Point(_lastTouchPosition.x, _lastTouchPosition.y)

  val isTablet by lazy { resources.getBoolean(R.bool.isTablet) }
  val currentUiLayoutModeState = MutableStateFlow<MainUiLayoutMode?>(null)
  val currentOrientation = MutableStateFlow<Int?>(null)

  val orientations = arrayOf(
    Configuration.ORIENTATION_PORTRAIT,
    Configuration.ORIENTATION_LANDSCAPE
  )

  private var _maxParentWidth: Int = 0
  val maxParentWidth: Int
    get() = _maxParentWidth
  private var _maxParentHeight: Int = 0
  val maxParentHeight: Int
    get() = _maxParentHeight

  val composeDensity by lazy {
    Density(
      appContext.resources.displayMetrics.density,
      appContext.resources.configuration.fontScale
    )
  }

  private val _drawerVisibilityFlow = MutableStateFlow<DrawerVisibility>(DrawerVisibility.Closed)
  val drawerVisibilityFlow: StateFlow<DrawerVisibility>
    get() = _drawerVisibilityFlow.asStateFlow()

  private val _currentPageFlow = mutableMapOf<MainUiLayoutMode, MutableStateFlow<CurrentPage>>()

  val defaultHorizPadding by lazy { if (isTablet) 12.dp else 8.dp }
  val defaultVertPadding by lazy { if (isTablet) 10.dp else 6.dp }

  private val _textTitleSizeSp = MutableStateFlow(0.sp)
  val textTitleSizeSp: StateFlow<TextUnit>
    get() = _textTitleSizeSp.asStateFlow()
  private val _textSubTitleSizeSp = MutableStateFlow(0.sp)
  val textSubTitleSizeSp: StateFlow<TextUnit>
    get() = _textSubTitleSizeSp.asStateFlow()

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
    _textTitleSizeSp.value = appSettings.textTitleSizeSp.read().sp
    _textSubTitleSizeSp.value = appSettings.textSubTitleSizeSp.read().sp
    _postCellCommentTextSizeSp.value = appSettings.postCellCommentTextSizeSp.read().sp
    _postCellSubjectTextSizeSp.value = appSettings.postCellSubjectTextSizeSp.read().sp
    _homeScreenLayoutType.value = appSettings.layoutType.read()
    _bookmarksScreenOnLeftSide.value = appSettings.bookmarksScreenOnLeftSide.read()

    if (!initialized.compareAndSet(false, true)) {
      return
    }

    coroutineScope.launch {
      combine(
        flow = homeScreenLayoutType,
        flow2 = currentOrientation,
        transform = { homeScreenLayout, orientation ->
          return@combine LayoutChangingSettings(
            orientation = orientation,
            homeScreenLayoutType = homeScreenLayout
          )
        }
      ).collectLatest { (orientation, layoutType) ->
        updateLayoutModeAndCurrentPage(layoutType, orientation)
      }
    }

    coroutineScope.launch {
      appSettings.textTitleSizeSp.valueFlow
        .collectLatest { value -> _textTitleSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.textSubTitleSizeSp.valueFlow
        .collectLatest { value -> _textSubTitleSizeSp.value = value.sp }
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

    logcat { "UiInfoManager initialization finished" }
  }

  fun currentPageFlow(uiLayoutMode: MainUiLayoutMode): StateFlow<CurrentPage> {
    return _currentPageFlow[uiLayoutMode]!!
  }

  fun currentPage(): CurrentPage? {
    return _currentPageFlow[currentUiLayoutModeState.value]?.value
  }

  fun currentPage(uiLayoutMode: MainUiLayoutMode): CurrentPage? {
    return _currentPageFlow[uiLayoutMode]?.value
  }

  fun clearCurrentPageFlow() {
    _currentPageFlow.clear()
  }

  fun updateMaxParentSize(availableWidth: Int, availableHeight: Int) {
    _maxParentWidth = availableWidth
    _maxParentHeight = availableHeight
  }

  fun setLastTouchPosition(x: Float, y: Float) {
    _lastTouchPosition.set(x.toInt(), y.toInt())
  }

  fun getOrCreateToolbarVisibilityInfo(screenKey: ScreenKey): ToolbarVisibilityInfo {
    return toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )
  }

  fun updateCurrentPageForLayoutMode(
    screenKey: ScreenKey,
    mainUiLayoutMode: MainUiLayoutMode
  ) {
    val currentUiLayoutMode = currentUiLayoutModeState.value!!
    if (currentUiLayoutMode != mainUiLayoutMode) {
      return
    }

    if (screenKey == currentPage(currentUiLayoutMode)?.screenKey) {
      return
    }

    val newCurrentPage = CurrentPage(screenKey, false)
    _currentPageFlow[currentUiLayoutMode]?.tryEmit(newCurrentPage)

    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    toolbarVisibilityInfo.update(postListScrollState = 1f)
  }

  fun updateCurrentPage(
    screenKey: ScreenKey,
    animate: Boolean = true
  ) {
    val currentUiLayoutMode = currentUiLayoutModeState.value!!

    if (screenKey == currentPage(currentUiLayoutMode)?.screenKey) {
      return
    }

    val newCurrentPage = CurrentPage(screenKey, animate)
    _currentPageFlow[currentUiLayoutMode]?.tryEmit(newCurrentPage)

    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    toolbarVisibilityInfo.update(postListScrollState = 1f)
  }

  fun onChildContentScrolling(
    screenKey: ScreenKey,
    delta: Float
  ) {
    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    var currentTransparency = toolbarVisibilityInfo.postListScrollState.value
    currentTransparency += (delta / toolbarHeight)

    if (currentTransparency < 0f) {
      currentTransparency = 0f
    }

    if (currentTransparency > 1f) {
      currentTransparency = 1f
    }

    toolbarVisibilityInfo.update(postListScrollState = currentTransparency)
  }

  fun onPostListDragStateChanged(screenKey: ScreenKey, dragging: Boolean) {
    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    toolbarVisibilityInfo.update(postListDragState = dragging)
  }

  fun onFastScrollerDragStateChanged(screenKey: ScreenKey, dragging: Boolean) {
    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    toolbarVisibilityInfo.update(fastScrollerDragState = dragging)
  }

  fun onPostListTouchingTopOrBottomStateChanged(screenKey: ScreenKey,touching: Boolean) {
    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    toolbarVisibilityInfo.update(postListTouchingTopOrBottomState = touching)
  }

  fun onChildScreenSearchStateChanged(screenKey: ScreenKey, searchQuery: String?) {
    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    val childScreenSearchInfo = ChildScreenSearchInfo(
      screenKey = screenKey,
      usingSearch = searchQuery != null
    )

    toolbarVisibilityInfo.update(childScreenSearchInfo = childScreenSearchInfo)
  }

  // TODO(KurobaEx):
  fun onLoadingErrorUpdatedOrRemoved(screenKey: ScreenKey, hasLoadError: Boolean) {
    val toolbarVisibilityInfo = toolbarVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { ToolbarVisibilityInfo() }
    )

    toolbarVisibilityInfo.update(hasLoadError = hasLoadError)
  }

  fun isDrawerOpenedOrOpening(): Boolean {
    return when (_drawerVisibilityFlow.value) {
      is DrawerVisibility.Drag -> true
      DrawerVisibility.Closed,
      DrawerVisibility.Closing -> false
      DrawerVisibility.Opened,
      DrawerVisibility.Opening -> true
    }
  }

  fun isDrawerFullyOpened(): Boolean {
    return _drawerVisibilityFlow.value is DrawerVisibility.Opened
  }

  fun openDrawer(withAnimation: Boolean = true) {
    if (withAnimation) {
      _drawerVisibilityFlow.value = DrawerVisibility.Opening
    } else {
      _drawerVisibilityFlow.value = DrawerVisibility.Opened
    }
  }

  fun closeDrawer(withAnimation: Boolean = true) {
    if (withAnimation) {
      _drawerVisibilityFlow.value = DrawerVisibility.Closing
    } else {
      _drawerVisibilityFlow.value = DrawerVisibility.Closed
    }
  }

  fun dragDrawer(isDragging: Boolean, progress: Float, velocity: Float) {
    _drawerVisibilityFlow.value = DrawerVisibility.Drag(isDragging, progress, velocity)
  }

  private suspend fun updateLayoutModeAndCurrentPage(
    layoutType: LayoutType,
    orientation: Int?
  ) {
    if (orientation == null) {
      val prevLayoutMode = currentUiLayoutModeState.value

      if (prevLayoutMode == null) {
        _currentPageFlow.clear()
      } else {
        _currentPageFlow.remove(prevLayoutMode)
      }

      currentUiLayoutModeState.value = null
      return
    }

    check(orientation in orientations) { "Unexpected orientation: ${orientation}" }

    val uiLayoutMode = when (layoutType) {
      LayoutType.Auto -> {
        when {
          isTablet -> MainUiLayoutMode.Split
          orientation == Configuration.ORIENTATION_PORTRAIT -> MainUiLayoutMode.Portrait
          else -> MainUiLayoutMode.Split
        }
      }
      LayoutType.Phone -> MainUiLayoutMode.Portrait
      LayoutType.Split -> MainUiLayoutMode.Split
    }

    if (!_currentPageFlow.containsKey(uiLayoutMode)) {
      val screenKey = when (uiLayoutMode) {
        MainUiLayoutMode.Portrait -> {
          val lastVisitedThread = appSettings.lastVisitedThread.read()
          if (lastVisitedThread == null) {
            CatalogScreen.SCREEN_KEY
          } else {
            ThreadScreen.SCREEN_KEY
          }
        }
        MainUiLayoutMode.Split -> {
          SplitScreenLayout.SCREEN_KEY
        }
      }

      _currentPageFlow[uiLayoutMode] = MutableStateFlow(CurrentPage(screenKey, false))
    }

    currentUiLayoutModeState.value = uiLayoutMode
  }

}

@Immutable
enum class MainUiLayoutMode {
  Portrait,
  Split
}

private data class LayoutChangingSettings(
  val orientation: Int?,
  val homeScreenLayoutType: LayoutType
)