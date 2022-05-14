package com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar

import android.os.Bundle
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

class PostsScreenLocalSearchToolbar(
  private val screenKey: ScreenKey,
  val onToolbarCreated: () -> Unit,
  val onToolbarDisposed: () -> Unit,
  val onSearchQueryUpdated: (String?) -> Unit,
  val closeSearch: suspend (toolbarKey: String) -> Unit
) : KurobaChildToolbar() {
  private val key = "${screenKey.key}_PostsScreenLocalSearchToolbar"
  private val state = State("${key}_state")

  override val toolbarState: ToolbarState? = null
  override val toolbarKey: String = key

  override fun onCreate() {
    onToolbarCreated()
  }

  override fun onDispose() {
    onToolbarDisposed()

    state.reset()
    onSearchQueryUpdated.invoke(null)
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun Content() {
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val searchDebouncer = remember { DebouncingCoroutineExecutor(coroutineScope) }
    val chanTheme = LocalChanTheme.current
    val parentBgColor = chanTheme.primaryColorCompose
    var searchQuery by state.searchQuery

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          keyboardController?.hide()
        }
      }
    )

    KurobaToolbarLayout(
      leftPart = {
        KurobaComposeIcon(
          modifier = Modifier
            .size(toolbarIconSize)
            .kurobaClickable(
              bounded = false,
              onClick = {
                coroutineScope.launch {
                  if (searchQuery.text.isNotEmpty()) {
                    searchQuery = TextFieldValue(text = "")

                    onSearchQueryUpdated.invoke("")
                  } else {
                    searchDebouncer.stop()
                    onSearchQueryUpdated.invoke(null)
                    closeSearch(toolbarKey)
                  }
                }
              }
            ),
          drawableId = if (searchQuery.text.isNotEmpty()) {
            R.drawable.ic_baseline_clear_24
          } else {
            R.drawable.ic_baseline_arrow_back_24
          }
        )
      },
      middlePart = {
        KurobaComposeCustomTextField(
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .focusable(),
          value = searchQuery,
          labelText = stringResource(R.string.toolbar_type_to_search_hint),
          parentBackgroundColor = parentBgColor,
          onValueChange = { updatedQuery ->
            if (searchQuery != updatedQuery) {
              searchQuery = updatedQuery

              searchDebouncer.post(timeout = 125L) {
                onSearchQueryUpdated.invoke(updatedQuery.text)
              }
            }
          }
        )
      },
      rightPart = null
    )
  }

  class State(
    override val saveableComponentKey: String
  ) : ToolbarState {
    val searchQuery = mutableStateOf(TextFieldValue())

    override fun saveState(): Bundle {
      return Bundle().apply {
        putString(SEARCH_QUERY_KEY, searchQuery.value.text)
      }
    }

    override fun restoreFromState(bundle: Bundle?) {
      bundle?.getString(SEARCH_QUERY_KEY)?.let { query -> searchQuery.value = TextFieldValue(text = query) }
    }

    fun reset() {
      searchQuery.value = TextFieldValue()
    }

    companion object {
      private const val SEARCH_QUERY_KEY = "search_query"
    }

  }

}