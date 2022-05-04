package com.github.k1rakishou.kurobaexlite.features.media

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings

class MediaViewerScreenState(
  private val appSettings: AppSettings,
  muteByDefault: Boolean = true
) {
  var images: SnapshotStateList<ImageLoadState>? = null
  val initialPage = mutableStateOf<Int?>(null)

  val currentlyLoadedMediaMap = mutableStateMapOf<Int, MediaState>()
  val mediaViewerUiVisible = mutableStateOf(false)

  private val _muteByDefault = mutableStateOf(muteByDefault)
  val muteByDefault: State<Boolean>
    get() = _muteByDefault

  fun isLoaded(): Boolean = initialPage.value != null && images != null
  fun requireImages(): SnapshotStateList<ImageLoadState> = requireNotNull(images) { "images not initialized yet!" }

  suspend fun toggleMediaViewerUiVisibility() {
    val newValue = !mediaViewerUiVisible.value

    mediaViewerUiVisible.value = newValue
    appSettings.mediaViewerUiVisible.write(newValue)
  }

  fun toggleMuteByDefault() {
    _muteByDefault.value = !_muteByDefault.value
  }

  companion object {
    fun Saver(
      appSettings: AppSettings
    ): Saver<MediaViewerScreenState, *> = Saver(
      save = {
        listOf<Any>(
          it.muteByDefault.value
        )
      },
      restore = { list ->
        MediaViewerScreenState(
          appSettings = appSettings,
          muteByDefault = list[0] as Boolean
        )
      }
    )
  }

}