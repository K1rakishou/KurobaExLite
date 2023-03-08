package com.github.k1rakishou.kurobaexlite.features.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine


@Composable
fun DrawerSearchInput(
  modifier: Modifier,
  searchQuery: TextFieldValue,
  searchingBookmarks: Boolean,
  onSearchQueryChanged: (TextFieldValue) -> Unit,
  onClearSearchQueryClicked: () -> Unit,
  onFocusChanged: (Boolean) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val focusRequester = FocusRequester.Default
  val focusManager = LocalFocusManager.current

  var isSearchInputFocused by remember { mutableStateOf(false) }

  val bgColor = remember(key1 = chanTheme.backColor) {
    return@remember if (ThemeEngine.isDarkColor(chanTheme.backColor)) {
      ThemeEngine.manipulateColor(chanTheme.backColor, 1.5f)
    } else {
      ThemeEngine.manipulateColor(chanTheme.backColor, 0.7f)
    }
  }

  val labelText = if (searchingBookmarks) {
    stringResource(id = R.string.type_to_search_bookmarks_hint)
  } else {
    stringResource(id = R.string.type_to_search_history_hint)
  }

  DisposableEffect(
    key1 = Unit,
    effect = { onDispose { onFocusChanged(false) } }
  )

  Column(
    modifier = modifier.then(
      Modifier
        .padding(horizontal = 8.dp, vertical = 8.dp)
        .background(color = bgColor, shape = RoundedCornerShape(corner = CornerSize(size = 4.dp)))
    ),
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    Row {
      val density = LocalDensity.current
      var textFieldHeight by remember { mutableStateOf(0.dp) }

      if (isSearchInputFocused || (searchQuery.text.isNotEmpty() && textFieldHeight > 0.dp)) {
        Spacer(modifier = Modifier.width(4.dp))

        KurobaComposeClickableIcon(
          modifier = Modifier.size(textFieldHeight),
          drawableId = R.drawable.ic_baseline_clear_24,
          onClick = {
            if (searchQuery.text.isNotEmpty() && textFieldHeight > 0.dp) {
              onClearSearchQueryClicked()
            } else if (isSearchInputFocused) {
              focusRequester.freeFocus()
              focusManager.clearFocus(force = true)
            }
          }
        )

        Spacer(modifier = Modifier.width(4.dp))
      }

      KurobaComposeCustomTextField(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .focusRequester(focusRequester)
          .onFocusChanged { focusState ->
            onFocusChanged(focusState.isFocused)
            isSearchInputFocused = focusState.isFocused
          }
          .onGloballyPositioned { layoutCoordinates ->
            with(density) { textFieldHeight = layoutCoordinates.size.height.toDp() }
          },
        value = searchQuery,
        parentBackgroundColor = chanTheme.backColor,
        keyboardOptions = KeyboardOptions(autoCorrect = false),
        drawBottomIndicator = false,
        singleLine = true,
        maxLines = 1,
        fontSize = 18.sp,
        textFieldPadding = remember { PaddingValues(vertical = 4.dp, horizontal = 4.dp) },
        labelText = labelText,
        onValueChange = { newValue -> onSearchQueryChanged(newValue) }
      )
    }

    Spacer(modifier = Modifier.height(4.dp))
  }
}
