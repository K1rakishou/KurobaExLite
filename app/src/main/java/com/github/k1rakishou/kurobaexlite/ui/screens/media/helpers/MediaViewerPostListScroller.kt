package com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl

class MediaViewerPostListScroller {

  private val _scrollEventFlow = MutableSharedFlow<Pair<HttpUrl, PostDescriptor>>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )

  val scrollEventFlow: SharedFlow<Pair<HttpUrl, PostDescriptor>>
    get() = _scrollEventFlow.asSharedFlow()

  fun onSwipedTo(fullImageUrl: HttpUrl, postDescriptor: PostDescriptor) {
    _scrollEventFlow.tryEmit(fullImageUrl to postDescriptor)
  }

}