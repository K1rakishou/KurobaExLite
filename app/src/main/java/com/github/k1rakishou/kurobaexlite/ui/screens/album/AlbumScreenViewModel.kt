package com.github.k1rakishou.kurobaexlite.ui.screens.album

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class AlbumScreenViewModel : BaseViewModel() {
  private val chanViewManager: ChanViewManager by inject(ChanViewManager::class.java)

  suspend fun loadAlbumFromPostStateList(
    chanDescriptor: ChanDescriptor,
    postStateList: List<State<PostCellData>>
  ): Album {
    return withContext(Dispatchers.Default) {
      val totalImages = mutableListWithCap<IPostImage>(32)
      var currentIndex = 0
      var imageToScrollTo: IPostImage? = null

      val lastViewedPostDescriptor = when (chanDescriptor) {
        is CatalogDescriptor -> chanViewManager.read(chanDescriptor)?.lastViewedPostDescriptor
        is ThreadDescriptor -> chanViewManager.read(chanDescriptor)?.lastViewedPostDescriptor
      }

      postStateList.forEachIndexed { index, postState ->
        val postCellData = postState.value

        if (postCellData.postDescriptor == lastViewedPostDescriptor) {
          if (postCellData.images.isNotNullNorEmpty()) {
            imageToScrollTo = postCellData.images.first()
          } else {
            imageToScrollTo = findFirstSuitableImageCloseToIndex(postStateList, index)
          }
        }

        postCellData.images?.let { postImages ->
          ++currentIndex
          totalImages.addAll(postImages)
        }
      }

      return@withContext Album(
        imageToScrollTo = imageToScrollTo,
        images = totalImages
      )
    }
  }

  private fun findFirstSuitableImageCloseToIndex(
    postStateList: List<State<PostCellData>>,
    startIndex: Int
  ): IPostImage? {
    // First, find next post with images
    for (index in startIndex until postStateList.size) {
      val images = postStateList.getOrNull(index)
        ?.value
        ?.images
        ?.takeIf { images -> images.isNotEmpty() }
        ?: continue

      return images.firstOrNull()
    }

    // If we failed to find next post the try to find previous post with images
    for (index in startIndex downTo 0) {
      val images = postStateList.getOrNull(index)
        ?.value
        ?.images
        ?.takeIf { images -> images.isNotEmpty() }
        ?: continue

      return images.firstOrNull()
    }

    // The has no images
    return null
  }

  @Stable
  data class Album(
    private val imageToScrollTo: IPostImage?,
    val images: List<IPostImage>
  ) {

    fun imageIndexByPostDescriptor(postDescriptor: PostDescriptor): Int? {
      return images
        .indexOfFirst { postImage -> postImage.ownerPostDescriptor == postDescriptor }
        .takeIf { index -> index >= 0 }
    }

    val scrollIndex by lazy {
      val index = images
        .indexOfFirst { postImage -> postImage.fullImageAsUrl == imageToScrollTo?.fullImageAsUrl }

      if (index < 0) {
        return@lazy 0
      }

      return@lazy index
    }

  }

}