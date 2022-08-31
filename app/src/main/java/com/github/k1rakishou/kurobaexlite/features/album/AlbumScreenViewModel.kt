package com.github.k1rakishou.kurobaexlite.features.album

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.sort.CatalogThreadSorter
import com.github.k1rakishou.kurobaexlite.helpers.sort.ThreadPostSorter
import com.github.k1rakishou.kurobaexlite.helpers.util.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.originalFileNameForPostCell
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

class AlbumScreenViewModel(
  private val appSettings: AppSettings,
  private val appResources: AppResources,
  private val chanViewManager: ChanViewManager,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val chanCache: ChanCache,
  private val mediaSaver: MediaSaver,
) : BaseViewModel() {
  private val _selectedImages = mutableStateMapOf<String, Unit>()
  val selectedImages: Map<String, Unit>
    get() = _selectedImages

  private val _allImageKeys = mutableSetWithCap<String>(128)

  private val _album = MutableStateFlow<Album?>(null)
  val album: StateFlow<Album?>
    get() = _album.asStateFlow()

  private val _snackbarFlow = MutableSharedFlow<String>()
  val snackbarFlow: SharedFlow<String>
    get() = _snackbarFlow.asSharedFlow()

  fun clearAllImageKeys() {
    _allImageKeys.clear()
  }

  fun isImageSelected(albumImage: AlbumImage): Boolean {
    return _selectedImages.containsKey(albumImage.postImage.serverFileName)
  }

  fun toggleImageSelection(albumImage: AlbumImage) {
    val key = albumImage.postImage.serverFileName

    if (_selectedImages.containsKey(key)) {
      _selectedImages.remove(key)
    } else {
      _selectedImages[key] = Unit
    }
  }

  fun toggleSelectionGlobal() {
    val allSelected = _allImageKeys.all { key -> key in _selectedImages }
    if (allSelected) {
      _selectedImages.clear()
    } else {
      val newMap = _allImageKeys.associateWith { Unit }

      _selectedImages.clear()
      _selectedImages.putAll(newMap)
    }
  }

  fun clearSelection() {
    _selectedImages.clear()
  }

  fun downloadSelectedImages(
    chanDescriptor: ChanDescriptor,
    onResult: (MediaSaver.ActiveDownload) -> Unit
  ) {
    val selectedImagesCopy = selectedImages.toMap()

    if (selectedImagesCopy.isEmpty()) {
      onResult(MediaSaver.ActiveDownload(0, 0, 0))
      return
    }

    viewModelScope.launch {
      val imagesToSave = mutableListOf<IPostImage>()

      val posts = when (chanDescriptor) {
        is CatalogDescriptor -> chanCache.getCatalogThreads(chanDescriptor)
        is ThreadDescriptor -> chanCache.getThreadPosts(chanDescriptor)
      }

      posts.forEach { postData ->
        postData.images?.forEach { postImage ->
          if (selectedImagesCopy.containsKey(postImage.serverFileName)) {
            imagesToSave += postImage
          }
        }
      }

      if (imagesToSave.isEmpty()) {
        return@launch
      }

      val activeDownload = mediaSaver.savePostImages(imagesToSave)
      onResult(activeDownload)
    }
  }

  private fun findFirstSuitableImageCloseToIndex(
    postDataList: List<IPostData>,
    startIndex: Int
  ): IPostImage? {
    // First, find next post with images
    for (index in startIndex until postDataList.size) {
      val images = postDataList.getOrNull(index)
        ?.images
        ?.takeIf { images -> images.isNotEmpty() }
        ?: continue

      return images.firstOrNull()
    }

    // If we failed to find next post the try to find previous post with images
    for (index in startIndex downTo 0) {
      val images = postDataList.getOrNull(index)
        ?.images
        ?.takeIf { images -> images.isNotEmpty() }
        ?: continue

      return images.firstOrNull()
    }

    return null
  }

  suspend fun loadAlbumAndListenForUpdates(chanDescriptor: ChanDescriptor) {
    logcat(TAG) { "loadAlbumAndListenForUpdates(${chanDescriptor})" }

    _album.emit(null)
    val album = loadAlbumInitial(chanDescriptor)
    album.albumImages.forEach { albumImage -> _allImageKeys.add(albumImage.postImage.serverFileName) }
    _album.emit(album)

    logcat(TAG) { "loadAlbumAndListenForUpdates() loaded ${album.albumImages.size} album images" }

    chanCache.listenForPostUpdates(chanDescriptor)
      .collect { postLoadResult ->
        if (postLoadResult.newPostsCount <= 0) {
          return@collect
        }

        val currentAlbum = _album.value
          ?: return@collect

        val newAlbumImages = mutableListOf<AlbumImage>()

        postLoadResult.newPosts.forEach { postData ->
          val images = postData.images
          if (images.isNullOrEmpty()) {
            return@forEach
          }

          images.forEach { postImage ->
            newAlbumImages += mapPostImageToAlbumImage(postImage)
          }
        }

        if (newAlbumImages.isEmpty()) {
          return@collect
        }

        newAlbumImages.forEach { albumImage ->
          _allImageKeys.add(albumImage.postImage.serverFileName)
        }

        logcat(TAG) {
          "loadAlbumAndListenForUpdates() Got ${newAlbumImages.size} new album images " +
            "from ChanCache for ${chanDescriptor}"
        }

        currentAlbum.appendNewAlbumImages(newAlbumImages)
        _snackbarFlow.emit(
          appResources.string(
            R.string.album_screen_new_album_images_added,
            newAlbumImages.size
          )
        )
      }
  }

  private suspend fun loadAlbumInitial(
    chanDescriptor: ChanDescriptor
  ): Album {
    return withContext(Dispatchers.Default) {
      val albumImages = mutableListWithCap<AlbumImage>(32)
      var currentIndex = 0
      var imageToScrollTo: IPostImage? = null

      val lastViewedPostDescriptor = when (chanDescriptor) {
        is CatalogDescriptor -> chanViewManager.read(chanDescriptor)?.lastViewedPostDescriptor
        is ThreadDescriptor -> chanViewManager.read(chanDescriptor)?.lastViewedPDForScroll
      }

      val postDataList = when (chanDescriptor) {
        is CatalogDescriptor -> {
          CatalogThreadSorter.sortCatalogPostData(
            catalogThreads = chanCache.getCatalogThreads(chanDescriptor),
            catalogSortSetting = appSettings.catalogSort.read()
          )
        }
        is ThreadDescriptor -> {
          ThreadPostSorter.sortThreadPostData(chanCache.getThreadPosts(chanDescriptor))
        }
      }

      postDataList.forEachIndexed { index, postData ->
        if (postData.postDescriptor == lastViewedPostDescriptor) {
          if (postData.images.isNotNullNorEmpty()) {
            imageToScrollTo = postData.images?.firstOrNull()
          } else {
            imageToScrollTo = findFirstSuitableImageCloseToIndex(postDataList, index)
          }
        }

        postData.images?.let { postImages ->
          ++currentIndex

          val mappedToAlbumImages = postImages
            .map { postImage -> mapPostImageToAlbumImage(postImage) }

          albumImages.addAll(mappedToAlbumImages)
        }
      }

      _allImageKeys.clear()
      _allImageKeys.addAll(albumImages.map { it.postImage.serverFileName })

      return@withContext Album(imageToScrollTo).also { album ->
        album.setNewAlbumImages(albumImages)
      }
    }
  }

  private suspend fun mapPostImageToAlbumImage(postImage: IPostImage): AlbumImage {
    val postSubject = parsedPostDataCache.getParsedPostData(postImage.ownerPostDescriptor)
      ?.parsedPostSubject

    val imageInfo = buildString {
      append(postImage.ext.uppercase(Locale.ENGLISH))
      append(" ")
      append(postImage.width.toString())
      append("x")
      append(postImage.height.toString())
      append(" ")
      append(postImage.fileSize.asReadableFileSize())
    }

    return AlbumImage(
      postImage = postImage,
      postSubject = postSubject,
      imageOriginalFileName = postImage.originalFileNameForPostCell(maxLength = (Int.MAX_VALUE / 2)),
      imageInfo = imageInfo
    )
  }

  @Stable
  data class Album(
    private val imageToScrollTo: IPostImage?
  ) {
    private val _albumImages = mutableStateListOf<AlbumImage>()
    val albumImages: List<AlbumImage>
      get() = _albumImages

    fun setNewAlbumImages(newAlbumImages: List<AlbumImage>) {
      if (newAlbumImages.isEmpty()) {
        return
      }

      Snapshot.withMutableSnapshot {
        _albumImages.clear()
        _albumImages.addAll(newAlbumImages)
      }
    }

    fun appendNewAlbumImages(newAlbumImages: List<AlbumImage>) {
      if (newAlbumImages.isEmpty()) {
        return
      }

      Snapshot.withMutableSnapshot {
        newAlbumImages.forEach { newAlbumImage ->
          val alreadyAdded = _albumImages.any { albumImage ->
            albumImage.postImage.fullImageAsString == newAlbumImage.postImage.fullImageAsString
          }

          if (alreadyAdded) {
            return@forEach
          }

          _albumImages += newAlbumImage
        }
      }
    }

    fun imageIndexByPostDescriptor(postDescriptor: PostDescriptor): Int? {
      return _albumImages
        .map { it.postImage }
        .indexOfFirst { postImage -> postImage.ownerPostDescriptor == postDescriptor }
        .takeIf { index -> index >= 0 }
    }

    val scrollIndex: Int
      get() {
        val index = _albumImages
          .map { it.postImage }
          .indexOfFirst { postImage -> postImage.fullImageAsUrl == imageToScrollTo?.fullImageAsUrl }

        if (index < 0) {
          return 0
        }

        return index
      }

  }

  @Stable
  data class AlbumImage(
    val postImage: IPostImage,
    val postSubject: String?,
    val imageOriginalFileName: String,
    val imageInfo: String
  )

  companion object {
    private const val TAG = "AlbumScreenViewModel"
  }

}