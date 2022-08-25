package com.github.k1rakishou.kurobaexlite.features.settings.items

import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import kotlinx.coroutines.flow.combine

abstract class SettingItem(
  val key: String,
  val title: String,
  val subtitle: AnnotatedString?,
  val dependencies: List<BooleanSetting>
) {
  protected val dependenciesEnabledState = mutableStateOf(false)

  @CallSuper
  @Composable
  open fun Content() {
    LaunchedEffect(
      key1 = dependencies,
      block = {
        if (dependencies.isEmpty()) {
          dependenciesEnabledState.value = true
          return@LaunchedEffect
        }

        combine(
          flows = dependencies.map { dependency -> dependency.listen() }.toTypedArray(),
          transform = { arrayOfStates -> arrayOfStates.all { enabled -> enabled } }
        ).collect { allEnabled ->
          dependenciesEnabledState.value = allEnabled
        }
      })
  }

  fun matchesQuery(searchQuery: String): Boolean {
    if (searchQuery.isEmpty()) {
      return true
    }

    return title.contains(other = searchQuery, ignoreCase = true) ||
      (subtitle?.text?.contains(other = searchQuery, ignoreCase = true) == true)
  }

}