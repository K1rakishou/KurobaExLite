package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun rememberPostBlinkAnimationState(): PostBlinkAnimationState {
  return remember { PostBlinkAnimationState() }
}

@Stable
class PostBlinkAnimationState {

  private val _blinkingPost = MutableStateFlow<PostDescriptor?>(null)

  val blinkEvents: StateFlow<PostDescriptor?>
    get() = _blinkingPost.asStateFlow()

  fun startBlinking(postDescriptor: PostDescriptor) {
    _blinkingPost.value = postDescriptor
  }

  fun stopBlinking() {
    _blinkingPost.value = null
  }

}