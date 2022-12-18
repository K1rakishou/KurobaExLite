package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Composable
fun rememberPostListSelectionState(postSelectionEnabled: Boolean = true): PostListSelectionState {
  return remember { PostListSelectionState(postSelectionEnabled) }
}

@Stable
class PostListSelectionState(
  private val postSelectionEnabled: Boolean
) {
  private val _selectedItems = mutableStateListOf<PostDescriptor>()

  private val _isInSelectionMode = mutableStateOf(false)
  val isInSelectionMode: State<Boolean>
    get() = _isInSelectionMode

  private val _selectedItemsUpdateFlow = MutableSharedFlow<List<PostDescriptor>>(extraBufferCapacity = 1)
  val selectedItemsUpdateFlow: SharedFlow<List<PostDescriptor>>
    get() = _selectedItemsUpdateFlow.asSharedFlow()

  fun isPostSelected(postDescriptor: PostDescriptor): Boolean {
    if (!postSelectionEnabled) {
      return false
    }

    return _selectedItems.contains(postDescriptor)
  }

  fun toggleSelection(postDescriptor: PostDescriptor) {
    if (_selectedItems.contains(postDescriptor)) {
      _selectedItems.remove(postDescriptor)
    } else {
      _selectedItems.add(postDescriptor)
    }

    _selectedItemsUpdateFlow.tryEmit(_selectedItems.toList())
    _isInSelectionMode.value = _selectedItems.isNotEmpty()
  }

  fun clearSelection() {
    _selectedItems.clear()
    _selectedItemsUpdateFlow.tryEmit(emptyList())
    _isInSelectionMode.value = false
  }

}