package com.github.k1rakishou.kurobaexlite.features.media

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaViewerScreenState(
  private val appSettings: AppSettings,
  muteByDefault: Boolean = true,
  initialPage: Int? = null
) {
  private var _mediaList = mutableStateListOf<ImageLoadState>()
  val mediaList: List<ImageLoadState>
    get() = _mediaList

  private val _currentPageIndex = mutableStateOf<Int?>(initialPage)
  val currentPageIndex: State<Int?>
    get() = _currentPageIndex

  private val _currentlyLoadedMediaMap = mutableStateMapOf<Int, MediaState>()
  val currentlyLoadedMediaMap: Map<Int, MediaState>
    get() = _currentlyLoadedMediaMap

  private val _mediaViewerUiVisible = mutableStateOf(false)
  val mediaViewerUiVisible: State<Boolean>
    get() = _mediaViewerUiVisible

  private val _muteByDefault = mutableStateOf(muteByDefault)
  val muteByDefault: State<Boolean>
    get() = _muteByDefault

  private val _mediaNavigationEventFlow = MutableSharedFlow<MediaNavigationEvent>(Channel.UNLIMITED)
  val mediaNavigationEventFlow: SharedFlow<MediaNavigationEvent>
    get() = _mediaNavigationEventFlow.asSharedFlow()

  fun init(images: List<ImageLoadState>?, initialPage: Int?, mediaViewerUiVisible: Boolean) {
    Snapshot.withMutableSnapshot {
      _mediaList.clear()

      if (images != null) {
        _mediaList.addAll(images)
      }

      if (_currentPageIndex.value == null) {
        _currentPageIndex.value = initialPage
      }

      _mediaViewerUiVisible.value = mediaViewerUiVisible
    }
  }

  fun addCurrentlyLoadedMediaState(page: Int, state: MediaState) {
    _currentlyLoadedMediaMap.put(page, state)
  }

  fun removeCurrentlyLoadedMediaState(page: Int) {
    _currentlyLoadedMediaMap.remove(page)
  }

  fun getMediaStateByIndex(index: Int?): MediaState? {
    if (index == null) {
      return null
    }

    return _currentlyLoadedMediaMap[index]
  }

  fun onCurrentPagerPageChanged(page: Int) {
    _currentPageIndex.value = page
  }

  fun goToPrevMedia() {
    _mediaNavigationEventFlow.tryEmit(MediaNavigationEvent.GoToPrev)
  }

  fun goToNextMedia() {
    _mediaNavigationEventFlow.tryEmit(MediaNavigationEvent.GoToNext)
  }

  fun isLoaded(): Boolean = _currentPageIndex.value != null && _mediaList.isNotEmpty()

  fun mediaListMutable(): SnapshotStateList<ImageLoadState> = _mediaList

  suspend fun toggleMediaViewerUiVisibility() {
    val newValue = !_mediaViewerUiVisible.value

    _mediaViewerUiVisible.value = newValue
    appSettings.mediaViewerUiVisible.write(newValue)
  }

  fun toggleMuteByDefault() {
    _muteByDefault.value = !_muteByDefault.value
  }

  fun onPageDisposed(postImageDataLoadState: ImageLoadState) {
    val imagesMut = _mediaList

    val indexOfThisImage = imagesMut.indexOfFirst { it.fullImageUrl == postImageDataLoadState.fullImageUrl }
    if (indexOfThisImage >= 0) {
      val prevPostImageData = imagesMut[indexOfThisImage].postImage
      imagesMut.set(indexOfThisImage, ImageLoadState.PreparingForLoading(prevPostImageData))
    }
  }

  fun reloadImage(page: Int, postImageDataLoadState: ImageLoadState.Ready) {
    val imagesMut = _mediaList

    imagesMut[page] = ImageLoadState.PreparingForLoading(postImageDataLoadState.postImage)
  }

  enum class MediaNavigationEvent {
    GoToPrev,
    GoToNext
  }

  companion object {
    fun Saver(
      appSettings: AppSettings
    ): Saver<MediaViewerScreenState, *> = Saver(
      save = {
        listOf<Any?>(
          it.muteByDefault.value,
          it.currentPageIndex.value
        )
      },
      restore = { list ->
        MediaViewerScreenState(
          appSettings = appSettings,
          muteByDefault = list[0] as Boolean,
          initialPage = list[1] as Int?
        )
      }
    )
  }

}