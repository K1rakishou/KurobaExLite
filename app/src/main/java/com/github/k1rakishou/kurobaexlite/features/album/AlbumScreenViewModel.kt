package com.github.k1rakishou.kurobaexlite.features.album

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.originalFileNameForPostCell
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class AlbumScreenViewModel : BaseViewModel() {
  private val chanViewManager: ChanViewManager by inject(ChanViewManager::class.java)
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)
  private val chanCache: ChanCache by inject(ChanCache::class.java)
  private val mediaSaver: MediaSaver by inject(MediaSaver::class.java)

  private val _selectedImages = mutableStateMapOf<String, Unit>()
  val selectedImages: Map<String, Unit>
    get() = _selectedImages

  private val _allImageKeys = mutableSetWithCap<String>(128)

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

  suspend fun loadAlbumFromPostStateList(
    chanDescriptor: ChanDescriptor,
    postCellDataList: List<PostCellData>
  ): Album {
    return withContext(Dispatchers.Default) {
      val albumImages = mutableListWithCap<AlbumImage>(32)
      var currentIndex = 0
      var imageToScrollTo: IPostImage? = null

      val lastViewedPostDescriptor = when (chanDescriptor) {
        is CatalogDescriptor -> chanViewManager.read(chanDescriptor)?.lastViewedPostDescriptor
        is ThreadDescriptor -> chanViewManager.read(chanDescriptor)?.lastViewedPDForScroll
      }

      postCellDataList.forEachIndexed { index, postCellData ->
        if (postCellData.postDescriptor == lastViewedPostDescriptor) {
          if (postCellData.images.isNotNullNorEmpty()) {
            imageToScrollTo = postCellData.images.first()
          } else {
            imageToScrollTo = findFirstSuitableImageCloseToIndex(postCellDataList, index)
          }
        }

        postCellData.images?.let { postImages ->
          ++currentIndex

          val mappedToAlbumImages = postImages.map { postImage ->
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

            return@map AlbumImage(
              postImage = postImage,
              postSubject = postSubject,
              imageOriginalFileName = postImage.originalFileNameForPostCell(maxLength = (Int.MAX_VALUE / 2)),
              imageInfo = imageInfo
            )
          }

          albumImages.addAll(mappedToAlbumImages)
        }
      }

      _allImageKeys.clear()
      _allImageKeys.addAll(albumImages.map { it.postImage.serverFileName })

      return@withContext Album(
        imageToScrollTo = imageToScrollTo,
        albumImages = albumImages
      )
    }
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
    postStateList: List<PostCellData>,
    startIndex: Int
  ): IPostImage? {
    // First, find next post with images
    for (index in startIndex until postStateList.size) {
      val images = postStateList.getOrNull(index)
        ?.images
        ?.takeIf { images -> images.isNotEmpty() }
        ?: continue

      return images.firstOrNull()
    }

    // If we failed to find next post the try to find previous post with images
    for (index in startIndex downTo 0) {
      val images = postStateList.getOrNull(index)
        ?.images
        ?.takeIf { images -> images.isNotEmpty() }
        ?: continue

      return images.firstOrNull()
    }

    return null
  }

  @Stable
  data class Album(
    private val imageToScrollTo: IPostImage?,
    val albumImages: List<AlbumImage>
  ) {

    fun imageIndexByPostDescriptor(postDescriptor: PostDescriptor): Int? {
      return albumImages
        .map { it.postImage }
        .indexOfFirst { postImage -> postImage.ownerPostDescriptor == postDescriptor }
        .takeIf { index -> index >= 0 }
    }

    val scrollIndex by lazy {
      val index = albumImages
        .map { it.postImage }
        .indexOfFirst { postImage -> postImage.fullImageAsUrl == imageToScrollTo?.fullImageAsUrl }

      if (index < 0) {
        return@lazy 0
      }

      return@lazy index
    }

  }

  @Stable
  data class AlbumImage(
    val postImage: IPostImage,
    val postSubject: String?,
    val imageOriginalFileName: String,
    val imageInfo: String
  )

}