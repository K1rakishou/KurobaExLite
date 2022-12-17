package com.github.k1rakishou.kurobaexlite.managers

import android.util.LruCache
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl

class RevealedSpoilerImages {
  @GuardedBy("itself")
  private val cache = LruCache<HttpUrl, Unit>(4096)

  private val _spoilerImageRevealedEvents = MutableSharedFlow<HttpUrl>(
    extraBufferCapacity = Channel.UNLIMITED
  )
  val spoilerImageRevealedEvents: SharedFlow<HttpUrl>
    get() = _spoilerImageRevealedEvents.asSharedFlow()

  fun isSpoilerImageRevealed(imageUrl: HttpUrl): Boolean {
    return synchronized(cache) { cache.get(imageUrl) != null }
  }

  fun onFullImageOpened(postImage: IPostImage) {
    if (postImage.thumbnailSpoiler == null) {
      return
    }

    val imageUrl = postImage.fullImageAsUrl

    synchronized(cache) {
      if (cache.put(imageUrl, Unit) == null) {
        _spoilerImageRevealedEvents.tryEmit(imageUrl)
      }
    }
  }

}