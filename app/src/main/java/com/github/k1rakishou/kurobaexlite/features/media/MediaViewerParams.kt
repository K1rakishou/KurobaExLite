package com.github.k1rakishou.kurobaexlite.features.media

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import kotlinx.parcelize.Parcelize
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl


@Immutable
sealed class MediaViewerParams : Parcelable {
  abstract val chanDescriptor: ChanDescriptor
  abstract val initialImageUrlString: String

  val initialImageUrl: HttpUrl
    get() {
      return when (this) {
        is Catalog -> this.initialImageUrlString.toHttpUrl()
        is Images -> this.initialImageUrlString.toHttpUrl()
        is Thread -> this.initialImageUrlString.toHttpUrl()
      }
    }

  @Parcelize
  data class Catalog(
    override val chanDescriptor: ChanDescriptor,
    override val initialImageUrlString: String
  ): MediaViewerParams()

  @Parcelize
  data class Thread(
    override val chanDescriptor: ChanDescriptor,
    override val initialImageUrlString: String
  ) : MediaViewerParams()

  @Parcelize
  data class Images(
    override val chanDescriptor: ChanDescriptor,
    val images: List<String>,
    override val initialImageUrlString: String
  ) : MediaViewerParams()
}