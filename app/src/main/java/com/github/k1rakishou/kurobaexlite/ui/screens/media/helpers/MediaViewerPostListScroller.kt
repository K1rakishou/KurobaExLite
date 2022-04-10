package com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl

class MediaViewerPostListScroller {

  private val _scrollEventFlow = MutableSharedFlow<ScrollInfo>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )

  val scrollEventFlow: SharedFlow<ScrollInfo>
    get() = _scrollEventFlow.asSharedFlow()

  fun onSwipedTo(screenKey: ScreenKey, fullImageUrl: HttpUrl, postDescriptor: PostDescriptor) {
    _scrollEventFlow.tryEmit(ScrollInfo(screenKey, fullImageUrl, postDescriptor))
  }

  data class ScrollInfo(
    val screenKey: ScreenKey,
    val imageUrl: HttpUrl,
    val postDescriptor: PostDescriptor,
  )

}