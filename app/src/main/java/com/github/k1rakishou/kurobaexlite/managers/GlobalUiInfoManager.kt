package com.github.k1rakishou.kurobaexlite.managers

import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutVisibility
import com.github.k1rakishou.kurobaexlite.helpers.SaveableComponent
import com.github.k1rakishou.kurobaexlite.helpers.getParcelableMap
import com.github.k1rakishou.kurobaexlite.helpers.getSerializableMap
import com.github.k1rakishou.kurobaexlite.helpers.putParcelableMap
import com.github.k1rakishou.kurobaexlite.helpers.putSerializableMap
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import com.github.k1rakishou.kurobaexlite.model.data.ui.ChildScreenSearchInfo
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.HideableUiVisibilityInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SplitScreenLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.logcat

class GlobalUiInfoManager(
  appScope: CoroutineScope,
  private val appResources: AppResources,
  private val appSettings: AppSettings
) : SaveableComponent {
  private val _initialized = mutableStateOf(false)
  val initialized: State<Boolean>
    get() = _initialized

  private val job = SupervisorJob()
  private val coroutineScope = CoroutineScope(Dispatchers.Main + job + appScope.coroutineContext)

  val toolbarHeight by lazy { appResources.dimension(R.dimen.toolbar_height) }
  val isTablet by lazy { appResources.boolean(R.bool.isTablet) }

  private val _lastTouchPosition = Point(0, 0)
  val lastTouchPosition: Point
    get() = Point(_lastTouchPosition.x, _lastTouchPosition.y)

  val orientations = arrayOf(
    Configuration.ORIENTATION_PORTRAIT,
    Configuration.ORIENTATION_LANDSCAPE
  )

  val defaultHorizPadding by lazy { if (isTablet) 12.dp else 8.dp }
  val defaultVertPadding by lazy { if (isTablet) 10.dp else 6.dp }

  private val hideableUiVisibilityInfoMap = mutableMapOf<ScreenKey, HideableUiVisibilityInfo>()
  private val replyLayoutVisibilityInfoMap = mutableMapOf<ScreenKey, MutableState<ReplyLayoutVisibility>>()

  val currentUiLayoutModeState = MutableStateFlow<MainUiLayoutMode?>(null)
  val currentOrientation = MutableStateFlow<Int?>(null)

  private val _notEnoughWidthForSplitLayoutFlow = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = Channel.UNLIMITED)
  val notEnoughWidthForSplitLayoutFlow: SharedFlow<Pair<Int, Int>>
    get() = _notEnoughWidthForSplitLayoutFlow.asSharedFlow()

  private var _totalScreenWidthState = mutableStateOf(0)
  val totalScreenWidthState: State<Int>
    get() = _totalScreenWidthState

  private var _totalScreenHeightState = mutableStateOf(0)
  val totalScreenHeightState: State<Int>
    get() = _totalScreenHeightState

  private var _currentDrawerVisibility: DrawerVisibility = DrawerVisibility.Closed
  val currentDrawerVisibility: DrawerVisibility
    get() = _currentDrawerVisibility

  private val _drawerVisibilityFlow = MutableSharedFlow<DrawerVisibility>(extraBufferCapacity = Channel.UNLIMITED)
  val drawerVisibilityFlow: SharedFlow<DrawerVisibility>
    get() = _drawerVisibilityFlow.asSharedFlow()

  private val _currentPageMapFlow = mutableMapOf<MainUiLayoutMode, MutableStateFlow<CurrentPage>>()

  // vvv Settings
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

  private val _historyScreenOnLeftSide = MutableStateFlow(false)
  val historyScreenOnLeftSide: StateFlow<Boolean>
    get() = _historyScreenOnLeftSide.asStateFlow()
  // ^^^ Settings

  override val key: String = SAVE_STATE_KEY

  override fun saveState(): Bundle {
    return Bundle().apply {
      putParcelableMap(TOOLBAR_VISIBILITY_INFO_MAP, hideableUiVisibilityInfoMap)

      putSerializableMap(
        key = CURRENT_PAGE_MAP,
        map = _currentPageMapFlow,
        mapper = { mainUiLayoutMode, currentPageStateFlow ->
          return@putSerializableMap Bundle().apply {
            putInt(MAIN_UI_LAYOUT_MODE, mainUiLayoutMode.value)
            putParcelable(CURRENT_PAGE, currentPageStateFlow.value)
          }
        }
      )

      putBoolean(IS_DRAWER_OPENED, _currentDrawerVisibility.isOpened)
    }
  }

  override fun restoreFromState(bundle: Bundle?) {
    if (bundle == null) {
      return
    }

    bundle.getParcelableMap<ScreenKey, HideableUiVisibilityInfo>(TOOLBAR_VISIBILITY_INFO_MAP)
      .let { restoredToolbarVisibilityMap ->
        hideableUiVisibilityInfoMap.clear()
        hideableUiVisibilityInfoMap.putAll(restoredToolbarVisibilityMap)
      }

    bundle.getSerializableMap(
      key = CURRENT_PAGE_MAP,
      mapper = { bundle ->
        val mainUiLayoutMode = MainUiLayoutMode.fromRawValue(bundle.getInt(MAIN_UI_LAYOUT_MODE, 0))
        val currentPage = bundle.getParcelable<CurrentPage>(CURRENT_PAGE)

        return@getSerializableMap Pair(mainUiLayoutMode, currentPage)
      }
    ).let { resultMap ->
      _currentPageMapFlow.clear()

      resultMap.entries.forEach { (mainUiLayoutMode, currentPage) ->
        if (currentPage == null) {
          return@forEach
        }

        _currentPageMapFlow[mainUiLayoutMode] = MutableStateFlow(currentPage)
      }
    }

    bundle.getBoolean(IS_DRAWER_OPENED, false).let { isDrawerOpened ->
      val drawerVisibility = if (isDrawerOpened) {
        DrawerVisibility.Opened
      } else {
        DrawerVisibility.Closed
      }

      updateDrawerVisibility(drawerVisibility)
    }
  }

  suspend fun init() {
    job.cancelChildren()

    _textTitleSizeSp.value = appSettings.textTitleSizeSp.read().sp
    _textSubTitleSizeSp.value = appSettings.textSubTitleSizeSp.read().sp
    _postCellCommentTextSizeSp.value = appSettings.postCellCommentTextSizeSp.read().sp
    _postCellSubjectTextSizeSp.value = appSettings.postCellSubjectTextSizeSp.read().sp
    _historyScreenOnLeftSide.value = appSettings.historyScreenOnLeftSide.read()

    coroutineScope.launch {
      val totalScreenWidthFlow = snapshotFlow { totalScreenWidthState.value }

      combine(
        flow = appSettings.layoutType.listen(),
        flow2 = currentOrientation,
        flow3 = totalScreenWidthFlow,
        transform = { homeScreenLayout, orientation, totalScreenWidth ->
          return@combine LayoutChangingSettings(
            orientation = orientation,
            homeScreenLayoutType = homeScreenLayout,
            totalScreenWidth = totalScreenWidth
          )
        }
      ).collectLatest { (orientation, layoutType, totalScreenWidth) ->
        updateLayoutModeAndCurrentPage(layoutType, orientation, totalScreenWidth)
      }
    }

    coroutineScope.launch {
      appSettings.textTitleSizeSp.listen()
        .collectLatest { value -> _textTitleSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.textSubTitleSizeSp.listen()
        .collectLatest { value -> _textSubTitleSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.postCellCommentTextSizeSp.listen()
        .collectLatest { value -> _postCellCommentTextSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.postCellSubjectTextSizeSp.listen()
        .collectLatest { value -> _postCellSubjectTextSizeSp.value = value.sp }
    }

    coroutineScope.launch {
      appSettings.historyScreenOnLeftSide.listen()
        .collectLatest { value -> _historyScreenOnLeftSide.value = value }
    }

    logcat { "UiInfoManager initialization finished" }
    _initialized.value = true
  }

  fun currentPageFlow(uiLayoutMode: MainUiLayoutMode): StateFlow<CurrentPage> {
    return _currentPageMapFlow[uiLayoutMode]?.asStateFlow()!!
  }

  fun currentPage(): CurrentPage? {
    return _currentPageMapFlow[currentUiLayoutModeState.value]?.value
  }

  fun currentPage(uiLayoutMode: MainUiLayoutMode): CurrentPage? {
    return _currentPageMapFlow[uiLayoutMode]?.value
  }

  fun updateMaxParentSize(availableWidth: Int, availableHeight: Int) {
    _totalScreenWidthState.value = availableWidth
    _totalScreenHeightState.value = availableHeight
  }

  fun setLastTouchPosition(x: Float, y: Float) {
    _lastTouchPosition.set(x.toInt(), y.toInt())
  }

  fun isAnyReplyLayoutOpened(): Boolean {
    return replyLayoutVisibilityInfoMap.entries
      .any { (_, replyLayoutVisibilityState) -> replyLayoutVisibilityState.value != ReplyLayoutVisibility.Closed  }
  }

  fun drawerVisibilityFlow(coroutineScope: CoroutineScope): StateFlow<DrawerVisibility> {
    return _drawerVisibilityFlow.stateIn(
      scope = coroutineScope,
      started = SharingStarted.Lazily,
      initialValue = _currentDrawerVisibility
    )
  }

  fun getOrCreateHideableUiVisibilityInfo(screenKey: ScreenKey): HideableUiVisibilityInfo {
    return hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )
  }

  fun replyLayoutVisibilityInfoStateForScreen(screenKey: ScreenKey): State<ReplyLayoutVisibility> {
    return replyLayoutVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { mutableStateOf(ReplyLayoutVisibility.Closed) }
    )
  }

  fun updateCurrentPageForLayoutMode(
    screenKey: ScreenKey,
    mainUiLayoutMode: MainUiLayoutMode
  ) {
    val currentUiLayoutMode = currentUiLayoutModeState.value
      ?: return

    if (currentUiLayoutMode != mainUiLayoutMode) {
      return
    }

    val actualScreenKey = layoutModeDependantScreenKey(screenKey, currentUiLayoutMode)

    if (actualScreenKey == currentPage(currentUiLayoutMode)?.screenKey) {
      return
    }

    val newCurrentPage = CurrentPage(actualScreenKey, false)
    _currentPageMapFlow[currentUiLayoutMode]?.tryEmit(newCurrentPage)

    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = actualScreenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(postListScrollState = 1f)
  }

  fun updateCurrentPage(
    screenKey: ScreenKey,
    animate: Boolean = true
  ) {
    val currentUiLayoutMode = currentUiLayoutModeState.value
      ?: return

    val actualScreenKey = layoutModeDependantScreenKey(
      screenKey = screenKey,
      currentUiLayoutMode = currentUiLayoutMode
    )

    if (actualScreenKey == currentPage(currentUiLayoutMode)?.screenKey) {
      return
    }

    val newCurrentPage = CurrentPage(actualScreenKey, animate)
    _currentPageMapFlow[currentUiLayoutMode]?.tryEmit(newCurrentPage)

    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = actualScreenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(postListScrollState = 1f)
  }

  fun onChildContentScrolling(
    screenKey: ScreenKey,
    delta: Float
  ) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    var currentTransparency = hideableUiVisibilityInfo.postListScrollState.value
    currentTransparency += (delta / toolbarHeight)

    if (currentTransparency < 0f) {
      currentTransparency = 0f
    }

    if (currentTransparency > 1f) {
      currentTransparency = 1f
    }

    hideableUiVisibilityInfo.update(postListScrollState = currentTransparency)
  }

  fun onPostListDragStateChanged(screenKey: ScreenKey, dragging: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(postListDragState = dragging)
  }

  fun onFastScrollerDragStateChanged(screenKey: ScreenKey, dragging: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(fastScrollerDragState = dragging)
  }

  fun onPostListTouchingTopOrBottomStateChanged(screenKey: ScreenKey,touching: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(postListTouchingTopOrBottomState = touching)
  }

  fun onChildScreenSearchStateChanged(screenKey: ScreenKey, searchQuery: String?) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    val childScreenSearchInfo = ChildScreenSearchInfo(
      screenKey = screenKey,
      usingSearch = searchQuery != null
    )

    hideableUiVisibilityInfo.update(childScreenSearchInfo = childScreenSearchInfo)
  }

  fun replyLayoutVisibilityStateChanged(
    screenKey: ScreenKey,
    replyLayoutVisibility: ReplyLayoutVisibility
  ) {
    val replyLayoutVisibilityState = replyLayoutVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { mutableStateOf(replyLayoutVisibility) }
    )
    replyLayoutVisibilityState.value = replyLayoutVisibility

    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(replyLayoutOpened = replyLayoutVisibility != ReplyLayoutVisibility.Closed)
  }

  fun onLoadingErrorUpdatedOrRemoved(screenKey: ScreenKey, hasLoadError: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(hasLoadError = hasLoadError)
  }

  fun isDrawerOpenedOrOpening(): Boolean {
    return when (_currentDrawerVisibility) {
      is DrawerVisibility.Fling -> true
      is DrawerVisibility.Drag -> true
      DrawerVisibility.Closed,
      DrawerVisibility.Closing -> false
      DrawerVisibility.Opened,
      DrawerVisibility.Opening -> true
    }
  }

  fun isDrawerFullyOpened(): Boolean {
    return _currentDrawerVisibility is DrawerVisibility.Opened
  }

  fun openDrawer(withAnimation: Boolean = true) {
    val drawerVisibility = if (withAnimation) {
      DrawerVisibility.Opening
    } else {
      DrawerVisibility.Opened
    }

    updateDrawerVisibility(drawerVisibility)
  }

  fun closeDrawer(withAnimation: Boolean = true) {
    val drawerVisibility = if (withAnimation) {
      DrawerVisibility.Closing
    } else {
      DrawerVisibility.Closed
    }

    updateDrawerVisibility(drawerVisibility)
  }

  fun dragDrawer(isDragging: Boolean, time: Long, dragX: Float) {
    val drawerVisibility = DrawerVisibility.Drag(
      time = time,
      isDragging = isDragging,
      dragX = dragX
    )
    updateDrawerVisibility(drawerVisibility)
  }

  fun flingDrawer(velocity: Velocity) {
    val drawerVisibility = DrawerVisibility.Fling(velocity)
    updateDrawerVisibility(drawerVisibility)
  }

  private fun layoutModeDependantScreenKey(
    screenKey: ScreenKey,
    currentUiLayoutMode: MainUiLayoutMode
  ): ScreenKey {
    return when (currentUiLayoutMode) {
      MainUiLayoutMode.Phone -> screenKey
      MainUiLayoutMode.Split -> {
        if (screenKey == CatalogScreen.SCREEN_KEY || screenKey == ThreadScreen.SCREEN_KEY) {
          return SplitScreenLayout.SCREEN_KEY
        }

        return screenKey
      }
    }
  }

  private fun updateDrawerVisibility(drawerVisibility: DrawerVisibility) {
    _drawerVisibilityFlow.tryEmit(drawerVisibility)
    _currentDrawerVisibility = drawerVisibility
  }

  private fun updateLayoutModeAndCurrentPage(
    layoutType: LayoutType,
    orientation: Int?,
    totalScreenWidth: Int
  ) {
    if (orientation == null) {
      val prevLayoutMode = currentUiLayoutModeState.value

      if (prevLayoutMode == null) {
        _currentPageMapFlow.clear()
      } else {
        _currentPageMapFlow.remove(prevLayoutMode)
      }

      currentUiLayoutModeState.value = null
      return
    }

    check(orientation in orientations) { "Unexpected orientation: ${orientation}" }

    val uiLayoutMode = when (layoutType) {
      LayoutType.Auto -> {
        when {
          isTablet -> MainUiLayoutMode.Split
          orientation == Configuration.ORIENTATION_PORTRAIT -> MainUiLayoutMode.Phone
          else -> MainUiLayoutMode.Split
        }
      }
      LayoutType.Phone -> MainUiLayoutMode.Phone
      LayoutType.Split -> MainUiLayoutMode.Split
    }

    MainUiLayoutMode.values().forEach { layoutMode ->
      if (!_currentPageMapFlow.containsKey(layoutMode)) {
        val screenKey = when (layoutMode) {
          MainUiLayoutMode.Phone -> CatalogScreen.SCREEN_KEY
          MainUiLayoutMode.Split -> CatalogScreen.SCREEN_KEY
        }

        _currentPageMapFlow[layoutMode] = MutableStateFlow(CurrentPage(screenKey, false))
      }
    }

    if (totalScreenWidth <= 0) {
      currentUiLayoutModeState.value = MainUiLayoutMode.Phone
      return
    }

    val availableWidthForCatalog = totalScreenWidth * CATALOG_SCREEN_WEIGHT
    val minCatalogSplitModelWidth = with(appResources.composeDensity) { minCatalogSplitModelWidthDp.roundToPx() }

    if (uiLayoutMode == MainUiLayoutMode.Split && availableWidthForCatalog < minCatalogSplitModelWidth) {
      _notEnoughWidthForSplitLayoutFlow.tryEmit(Pair(availableWidthForCatalog.toInt(), minCatalogSplitModelWidth))
      currentUiLayoutModeState.value = MainUiLayoutMode.Phone
    } else {
      currentUiLayoutModeState.value = uiLayoutMode
    }
  }

  companion object {
    const val SAVE_STATE_KEY = "com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager"

    private const val TOOLBAR_VISIBILITY_INFO_MAP = "toolbar_visibility_info_map"
    private const val CURRENT_PAGE_MAP = "current_page_map"
    private const val MAIN_UI_LAYOUT_MODE = "main_ui_layout_mode"
    private const val CURRENT_PAGE = "current_page"
    private const val IS_DRAWER_OPENED = "is_drawer_opened"

    const val CATALOG_SCREEN_WEIGHT = .4f
    const val THREAD_SCREEN_WEIGHT = .6f

    private val minCatalogSplitModelWidthDp = 200.dp
  }

}

@Immutable
enum class MainUiLayoutMode(val value: Int) {
  Phone(0),
  Split(1);

  companion object {
    fun fromRawValue(rawValue: Int): MainUiLayoutMode {
      return values().firstOrNull { it.value == rawValue } ?: Phone
    }
  }
}

private data class LayoutChangingSettings(
  val orientation: Int?,
  val homeScreenLayoutType: LayoutType,
  val totalScreenWidth: Int
)