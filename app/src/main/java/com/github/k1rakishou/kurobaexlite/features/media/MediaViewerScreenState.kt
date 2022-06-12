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
  private var _images = mutableStateListOf<ImageLoadState>()
  val images: List<ImageLoadState>
    get() = _images

  private val _initialPage = mutableStateOf<Int?>(initialPage)
  val initialPage: State<Int?>
    get() = _initialPage

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

  private var _lastOpenedPage: Int? = null

  fun init(images: List<ImageLoadState>?, initialPage: Int?, mediaViewerUiVisible: Boolean) {
    Snapshot.withMutableSnapshot {
      _images.clear()

      if (images != null) {
        _images.addAll(images)
      }

      if (_initialPage.value == null) {
        _initialPage.value = initialPage
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

  fun onCurrentPagerPageChanged(page: Int) {
    _lastOpenedPage = page
  }

  fun goToPrevMedia() {
    _mediaNavigationEventFlow.tryEmit(MediaNavigationEvent.GoToPrev)
  }

  fun goToNextMedia() {
    _mediaNavigationEventFlow.tryEmit(MediaNavigationEvent.GoToNext)
  }

  fun isLoaded(): Boolean = _initialPage.value != null && _images.isNotEmpty()
  fun imagesMutable(): SnapshotStateList<ImageLoadState> = _images

  suspend fun toggleMediaViewerUiVisibility() {
    val newValue = !_mediaViewerUiVisible.value

    _mediaViewerUiVisible.value = newValue
    appSettings.mediaViewerUiVisible.write(newValue)
  }

  fun toggleMuteByDefault() {
    _muteByDefault.value = !_muteByDefault.value
  }

  fun onPageDisposed(postImageDataLoadState: ImageLoadState) {
    val imagesMut = _images

    val indexOfThisImage = imagesMut.indexOfFirst { it.fullImageUrl == postImageDataLoadState.fullImageUrl }
    if (indexOfThisImage >= 0) {
      val prevPostImageData = imagesMut[indexOfThisImage].postImage
      imagesMut.set(indexOfThisImage, ImageLoadState.PreparingForLoading(prevPostImageData))
    }
  }

  fun reloadImage(page: Int, postImageDataLoadState: ImageLoadState.Ready) {
    val imagesMut = _images

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
          it._lastOpenedPage ?: it.initialPage.value
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