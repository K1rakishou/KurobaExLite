package com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar

import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarText
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

class PostsScreenSelectionToolbar<T : PostsScreenSelectionToolbar.State.SelectableItem>(
  private val screenKey: ScreenKey,
  private val onCancelSelection: () -> Unit,
  private val onScreenshotPosts: (List<T>) -> Unit
) : KurobaChildToolbar() {

  private val key = "${screenKey.key}PostsScreenSelectionToolbar"
  private val state = State<T>("${key}_state")

  override val toolbarKey: String = key
  override val toolbarState: ToolbarState = state

  fun onSelectedPostsUpdated(selectedPostDescriptors: List<PostDescriptor>) {
    val selectablePosts = selectedPostDescriptors.map { State.SelectablePost(it) }.toList() as List<T>
    state.onSelectedItemsUpdated(selectablePosts)
  }

  @Composable
  override fun Content() {
    LaunchedEffect(
      key1 = Unit,
      block = {
        state.iconClickEvents.collect { key ->
          when (key) {
            State.Icons.Close -> onCancelSelection()
            State.Icons.Screenshot -> onScreenshotPosts(state.selectedItemsAsList())
          }
        }
      }
    )

    KurobaToolbarLayout(
      leftPart = {
        state.leftIcon.Content(onClick = { key -> state.onIconClicked(key) })
      },
      middlePart = {
        val selectedItemsCount by state.selectedItemsCount

        KurobaToolbarText(
          text = stringResource(id = R.string.posts_screen_selection_toolbar_title, selectedItemsCount),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 16.sp
        )
      },
      rightPart = {
        state.rightIcons.fastForEach { toolbarIcon ->
          toolbarIcon.Content(onClick = { key -> state.onIconClicked(key) })
        }
      }
    )
  }

  class State<T : State.SelectableItem>(
    override val saveableComponentKey: String
  ) : ToolbarState {

    val leftIcon = KurobaToolbarIcon(
      key = Icons.Close,
      drawableId = R.drawable.ic_baseline_close_24
    )

    val rightIcons = listOf(
      KurobaToolbarIcon(
        key = Icons.Screenshot,
        drawableId = R.drawable.ic_baseline_photo_camera_24,
        enabled = true
      ),
    )

    private val _iconClickEvents = MutableSharedFlow<Icons>(extraBufferCapacity = Channel.UNLIMITED)
    val iconClickEvents: SharedFlow<Icons>
      get() = _iconClickEvents.asSharedFlow()

    private val _selectedItems = mutableListOf<T>()
    val selectedItems: List<T>
      get() = _selectedItems

    private val _selectedItemsCount = mutableStateOf(0)
    val selectedItemsCount: androidx.compose.runtime.State<Int>
      get() = _selectedItemsCount

    override fun saveState(): Bundle {
      return Bundle().apply {
        putParcelableArray(SELECTED_ITEMS, selectedItems.toTypedArray<Parcelable>())
      }
    }

    override fun restoreFromState(bundle: Bundle?) {
      Snapshot.withMutableSnapshot {
        bundle?.getParcelableArray(SELECTED_ITEMS)
          ?.let { selectedItemsFromBundle ->
            selectedItemsFromBundle.forEach { selectedItem ->
              _selectedItems.add(selectedItem as T)
            }
          }

        _selectedItemsCount.value = _selectedItems.size
      }
    }

    fun onSelectedItemsUpdated(selectedItems: List<T>) {
      _selectedItems.clear()
      _selectedItems.addAll(selectedItems)

      _selectedItemsCount.value = _selectedItems.size
    }

    fun onIconClicked(icons: Icons) {
      _iconClickEvents.tryEmit(icons)
    }

    fun selectedItemsAsList(): List<T> {
      return selectedItems.toList()
    }

    enum class Icons {
      Close,
      Screenshot
    }

    interface SelectableItem : Parcelable {
      val key: String
    }

    @Parcelize
    data class SelectablePost(
      val postDescriptor: PostDescriptor
    ) : SelectableItem {
      @IgnoredOnParcel
      override val key: String = postDescriptor.asKey()
    }

    companion object {
      private const val SELECTED_ITEMS = "selected_items"
    }

  }

}