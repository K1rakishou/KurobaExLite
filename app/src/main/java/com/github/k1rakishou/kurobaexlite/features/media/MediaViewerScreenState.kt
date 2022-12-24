package com.github.k1rakishou.kurobaexlite.features.media

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.saveable
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableIteration
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import logcat.logcat

@Stable
class MediaViewerScreenState(
  private val savedStateHandle: SavedStateHandle,
  private val appSettings: AppSettings,
  private val appResources: AppResources,
  private val chanPostCache: IChanPostCache
) {
  var chanDescriptor: ChanDescriptor? = null
    private set

  private var sourceType: MediaViewerScreenViewModel.SourceType? = null

  private val scope = KurobaCoroutineScope()

  private val _mediaList = mutableStateListOf<ImageLoadState>()
  val mediaList: List<ImageLoadState>
    get() = _mediaList

  // Used for scroll position restoration after configuration change, etc.
  private val _currentPageIndex = savedStateHandle.saveable(
    key = "current_page_index",
    stateSaver = autoSaver(),
    init = { mutableStateOf<Int?>(null) }
  )
  val currentPageIndex: State<Int?>
    get() = _currentPageIndex

  private val _currentlyLoadedMediaMap = mutableStateMapOf<Int, MediaState>()
  val currentlyLoadedMediaMap: Map<Int, MediaState>
    get() = _currentlyLoadedMediaMap

  private val _mediaViewerUiVisible = mutableStateOf(false)
  val mediaViewerUiVisible: State<Boolean>
    get() = _mediaViewerUiVisible

  private val _muteByDefault = savedStateHandle.saveable(
    key = "mute_by_default",
    stateSaver = autoSaver(),
    init = { mutableStateOf<Boolean>(true) }
  )
  val muteByDefault: State<Boolean>
    get() = _muteByDefault

  private val _mediaNavigationEventFlow = MutableSharedFlow<MediaNavigationEvent>(Channel.UNLIMITED)
  val mediaNavigationEventFlow: SharedFlow<MediaNavigationEvent>
    get() = _mediaNavigationEventFlow.asSharedFlow()

  // Used to scroll after the state was re-initialized, after, for example,
  // the user clicked a different media when the viewer is minimized
  private val _scrollToMediaFlow = MutableSharedFlow<Pair<Boolean, Int>>(extraBufferCapacity = Channel.UNLIMITED)
  val scrollToMediaFlow: SharedFlow<Pair<Boolean, Int>>
    get() = _scrollToMediaFlow.asSharedFlow()

  private val _snackbarFlow = MutableSharedFlow<String>()
  val snackbarFlow: SharedFlow<String>
    get() = _snackbarFlow.asSharedFlow()

  private val _newImagesAddedFlow = MutableSharedFlow<Unit>()
  val newImagesAddedFlow: SharedFlow<Unit>
    get() = _newImagesAddedFlow.asSharedFlow()

  fun init(
    chanDescriptor: ChanDescriptor,
    images: List<ImageLoadState>,
    pageIndex: Int,
    sourceType: MediaViewerScreenViewModel.SourceType,
    mediaViewerUiVisible: Boolean
  ) {
    scope.cancelChildren()

    Snapshot.withMutableSnapshot {
      _currentlyLoadedMediaMap.clear()

      val descriptorsAreTheSame = this.chanDescriptor == chanDescriptor
      val longScroll = !descriptorsAreTheSame

      if (!descriptorsAreTheSame || _mediaList.isEmpty() || this.sourceType != sourceType) {
        _mediaList.clear()
        _mediaList.addAll(images)
        _currentPageIndex.value = null

        this.chanDescriptor = chanDescriptor
        this.sourceType = sourceType
      }

      if (_currentPageIndex.value == null) {
        _currentPageIndex.value = pageIndex
      }

      _scrollToMediaFlow.tryEmit(Pair(longScroll, pageIndex))
      _mediaViewerUiVisible.value = mediaViewerUiVisible
    }

    if (sourceType == MediaViewerScreenViewModel.SourceType.CatalogOrThread) {
      scope.launch {
        logcat(TAG) { "init() listening for \'$chanDescriptor\' post updates" }

        listenForPostUpdates(chanDescriptor)
      }
    }
  }

  fun destroy() {
    scope.cancelChildren()

    Snapshot.withMutableSnapshot {
      chanDescriptor = null
      _currentPageIndex.value = null
      _currentlyLoadedMediaMap.clear()
    }
  }

  fun addCurrentlyLoadedMediaState(page: Int, state: MediaState) {
    _currentlyLoadedMediaMap.put(page, state)
  }

  fun removeCurrentlyLoadedMediaState(currentPage: Int, pageCount: Int) {
    val excludeFrom = (currentPage - MAX_VISIBLE_PAGES).coerceAtLeast(0)
    val excludeTo = (currentPage + MAX_VISIBLE_PAGES).coerceAtMost(pageCount - 1)

    _currentlyLoadedMediaMap.entries.mutableIteration { mutableIterator, mutableEntry ->
      val page = mutableEntry.key

      if (page < excludeFrom || page > excludeTo) {
        mutableIterator.remove()
      }

      return@mutableIteration true
    }
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

    val indexOfThisImage = imagesMut.indexOfFirst { it.uniqueKey() == postImageDataLoadState.uniqueKey() }
    if (indexOfThisImage >= 0) {
      val prevPostImageData = imagesMut[indexOfThisImage].postImage
      imagesMut.set(indexOfThisImage, ImageLoadState.PreparingForLoading(prevPostImageData))
    }
  }

  fun reloadImage(page: Int, postImageDataLoadState: ImageLoadState.Ready) {
    val imagesMut = _mediaList

    imagesMut[page] = ImageLoadState.PreparingForLoading(postImageDataLoadState.postImage)
  }

  private suspend fun listenForPostUpdates(chanDescriptor: ChanDescriptor) {
    chanPostCache.listenForPostUpdates(chanDescriptor)
      .collect { postLoadResult ->
        if (postLoadResult.newPostsCount <= 0) {
          return@collect
        }

        val newMediaList = mutableListOf<ImageLoadState>()

        postLoadResult.newPosts.forEach { postData ->
          val images = postData.images
          if (images.isNullOrEmpty()) {
            return@forEach
          }

          images.forEach { postImage ->
            newMediaList += ImageLoadState.PreparingForLoading(postImage)
          }
        }

        if (newMediaList.isEmpty()) {
          return@collect
        }

        logcat(TAG) {
          "listenForPostUpdates() Got ${newMediaList.size} new medias " +
            "from ChanCache for ${chanDescriptor}"
        }

        Snapshot.withMutableSnapshot {
          newMediaList.forEach { newMedia ->
            val alreadyAdded = _mediaList.any { imageLoadState ->
              imageLoadState.postImage.uniqueKey() == newMedia.postImage.uniqueKey()
            }

            if (alreadyAdded) {
              return@forEach
            }

            _mediaList += newMedia
          }
        }

        _snackbarFlow.emit(
          appResources.string(
            R.string.media_viewer_new_images_added,
            newMediaList.size
          )
        )
        _newImagesAddedFlow.emit(Unit)
      }
  }

  enum class MediaNavigationEvent {
    GoToPrev,
    GoToNext
  }

  companion object {
    private const val TAG = "MediaViewerScreenState"

    private const val MAX_VISIBLE_PAGES = 3
  }

}