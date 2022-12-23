package com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar

import android.os.Bundle
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.util.freeFocusSafe
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class PostsScreenLocalSearchToolbar(
  private val screenKey: ScreenKey,
  val currentSiteKey: () -> SiteKey?,
  val onToolbarCreated: () -> Unit,
  val onToolbarDisposed: () -> Unit,
  val onSearchQueryUpdated: (String?) -> Unit,
  val onGlobalSearchIconClicked: (String) -> Unit,
  val showFoundPostsInPopup: (List<PostDescriptor>) -> Unit,
  val closeSearch: suspend (toolbarKey: String) -> Unit
) : KurobaChildToolbar() {
  private val siteManager by inject<SiteManager>(SiteManager::class.java)

  private val key = "${screenKey.key}_PostsScreenLocalSearchToolbar"
  private val state = State("${key}_state")

  override val toolbarState: ToolbarState? = null
  override val toolbarKey: String = key

  val searchQuery: String
    get() = state.searchQuery.value.text
  val foundEntries: androidx.compose.runtime.State<List<PostDescriptor>>
    get() = state.foundEntriesState

  override fun onCreate() {
    super.onCreate()

    check(screenKey == CatalogScreen.SCREEN_KEY || screenKey == ThreadScreen.SCREEN_KEY) {
      "Unsupported screenKey: $screenKey"
    }

    onToolbarCreated()
  }

  override fun onDispose() {
    super.onDispose()
    onToolbarDisposed()

    state.reset()
    onSearchQueryUpdated.invoke(null)
  }

  fun onSearchUpdated(postsMatchedBySearchQuery: Set<PostDescriptor>) {
    state.onSearchUpdated(postsMatchedBySearchQuery)
  }

  fun firstEntry(): PostDescriptor? {
    return state.firstEntry()
  }

  fun prevEntry(): PostDescriptor? {
    return state.prevEntry()
  }

  fun nextEntry(): PostDescriptor? {
    return state.nextEntry()
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun Content() {
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val searchDebouncer = remember { DebouncingCoroutineExecutor(coroutineScope) }
    val chanTheme = LocalChanTheme.current
    val parentBgColor = chanTheme.backColor
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
        KurobaComposeClickableIcon(
          modifier = Modifier
            .size(toolbarIconSize)
            .padding(toolbarIconPadding),
          drawableId = if (searchQuery.text.isNotEmpty()) {
            R.drawable.ic_baseline_clear_24
          } else {
            R.drawable.ic_baseline_arrow_back_24
          },
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
        )
      },
      middlePart = {
        val keyboardOptions = remember { KeyboardOptions(autoCorrect = false) }
        val focusRequest = remember { FocusRequester() }

        DisposableEffect(
          key1 = Unit,
          effect = {
            val job = coroutineScope.launch {
              delay(64)
              focusRequest.requestFocus()
            }

            onDispose {
              job.cancel()
              focusRequest.freeFocusSafe()
            }
          }
        )

        KurobaComposeCustomTextField(
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .focusable()
            .focusRequester(focusRequest),
          value = searchQuery,
          labelText = stringResource(R.string.type_to_search_hint),
          singleLine = true,
          maxLines = 1,
          keyboardOptions = keyboardOptions,
          parentBackgroundColor = parentBgColor,
          keyboardActions = KeyboardActions(onDone = { onSearchQueryUpdated.invoke(searchQuery.text) }),
          onValueChange = { updatedQuery ->
            if (searchQuery != updatedQuery) {
              searchQuery = updatedQuery

              searchDebouncer.post(timeout = 250L) {
                onSearchQueryUpdated.invoke(updatedQuery.text)
              }
            }
          }
        )
      },
      rightPart = buildRightPart()
    )
  }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun buildRightPart(): @Composable (RowScope.() -> Unit) {
    return {
      val searchQuery by state.searchQuery
      val foundEntries by state.foundEntriesState
      val currentScrolledEntryIndex by state.currentScrolledEntryIndexState

      Row(
        modifier = Modifier
          .fillMaxHeight()
          .wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val isSearchQueryLengthGood = searchQuery.text.length >= PostsState.MIN_SEARCH_QUERY_LENGTH
        val localSoftwareKeyboardController = LocalSoftwareKeyboardController.current

        if (isSearchQueryLengthGood) {
          val totalFoundText = if (foundEntries.isEmpty()) {
            "---"
          } else {
            val current = currentScrolledEntryIndex?.plus(1) ?: "?"
            "${current} / ${foundEntries.size}"
          }

          KurobaComposeCard(
            modifier = Modifier
              .kurobaClickable(
                enabled = foundEntries.isNotEmpty(),
                onClick = {
                  if (foundEntries.isNotEmpty()) {
                    localSoftwareKeyboardController?.hide()
                    showFoundPostsInPopup(foundEntries)
                  }
                }
              )
          ) {
            KurobaComposeText(
              modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              text = totalFoundText,
            )
          }
        }

        val siteKey = currentSiteKey()

        val siteSupportsGlobalSearch = remember(key1 = siteKey) {
          if (siteKey == null) {
            return@remember false
          }

          return@remember siteManager.bySiteKey(siteKey)
            ?.globalSearchInfo()
            ?.supportsGlobalSearch
            ?: false
        }

        if (siteSupportsGlobalSearch && screenKey == CatalogScreen.SCREEN_KEY) {
          Spacer(modifier = Modifier.width(8.dp))

          KurobaComposeCard(
            modifier = Modifier
              .kurobaClickable(
                onClick = { onGlobalSearchIconClicked(searchQuery.text) }
              )
          ) {
            KurobaComposeText(
              modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              text = stringResource(id = R.string.posts_screen_search_toolbar_global_search),
            )
          }
        }
      }
    }
  }

  class State(
    override val saveableComponentKey: String
  ) : ToolbarState {
    val searchQuery = mutableStateOf(TextFieldValue())

    val foundEntriesState = mutableStateOf<List<PostDescriptor>>(emptyList())
    val currentScrolledEntryIndexState = mutableStateOf<Int?>(null)

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

    fun onSearchUpdated(postsMatchedBySearchQuery: Set<PostDescriptor>) {
      foundEntriesState.value = postsMatchedBySearchQuery.toList()

      if (postsMatchedBySearchQuery.isEmpty()) {
        currentScrolledEntryIndexState.value = null
      } else {
        currentScrolledEntryIndexState.value = 0
      }
    }

    fun firstEntry(): PostDescriptor? {
      val foundEntries = foundEntriesState.value
      if (foundEntries.isEmpty()) {
        currentScrolledEntryIndexState.value = null
        return null
      }

      return foundEntries.getOrNull(0)
    }

    fun prevEntry(): PostDescriptor? {
      val foundEntries = foundEntriesState.value
      if (foundEntries.isEmpty()) {
        currentScrolledEntryIndexState.value = null
        return null
      }

      var prevIndex = currentScrolledEntryIndexState.value?.minus(1) ?: 0
      if (prevIndex < 0) {
        prevIndex = foundEntries.lastIndex
      }

      currentScrolledEntryIndexState.value = prevIndex

      return foundEntries.getOrNull(prevIndex)
    }

    fun nextEntry(): PostDescriptor? {
      val foundEntries = foundEntriesState.value
      if (foundEntries.isEmpty()) {
        currentScrolledEntryIndexState.value = null
        return null
      }

      var nextIndex = currentScrolledEntryIndexState.value?.plus(1) ?: 0
      if (nextIndex > foundEntries.lastIndex) {
        nextIndex = 0
      }

      currentScrolledEntryIndexState.value = nextIndex

      return foundEntries.getOrNull(nextIndex)
    }

    companion object {
      private const val SEARCH_QUERY_KEY = "search_query"
    }

  }

}