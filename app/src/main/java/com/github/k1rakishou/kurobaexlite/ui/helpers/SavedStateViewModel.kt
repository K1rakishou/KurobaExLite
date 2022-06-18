package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.os.Bundle
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.setValue
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.savedstate.SavedStateRegistryOwner
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

class SavedStateViewModel(
  private val handle: SavedStateHandle
) : ViewModel() {

  @OptIn(SavedStateHandleSaveableApi::class)
  fun <T : Any> mutableSaveable(
    key: String,
    saver: Saver<T, out Any> = autoSaver<T>(),
    init: () -> T,
  ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> {
    return PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> { _, _ ->
      val mutableState: MutableState<T> = handle.saveable<T>(
        key = key,
        stateSaver = saver,
        init = { mutableStateOf(init()) }
      )

      // Create a property that delegates to the mutableState
      return@PropertyDelegateProvider object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T =
          mutableState.getValue(thisRef, property)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
          mutableState.setValue(thisRef, property, value)
      }
    }
  }

  @OptIn(SavedStateHandleSaveableApi::class)
  fun <T : Any> saveable(
    key: String,
    saver: Saver<T, out Any> = autoSaver<T>(),
    init: () -> T,
  ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> {
    return PropertyDelegateProvider { _, _ ->
      val state: State<T> = handle.saveable<T>(
        key = key,
        stateSaver = saver,
        init = { mutableStateOf(init()) }
      )

      return@PropertyDelegateProvider ReadOnlyProperty<Any?, T> { thisRef, property ->
        state.getValue(thisRef, property)
      }
    }
  }

  fun <T : Any> getArgumentOrNull(
    key: String
  ): T? {
    return handle.get(key)
  }

  fun <T : Any?> listenForArgumentsAsStateFlow(
    key: String,
    initialValue: T
  ): StateFlow<T> {
    return handle.getStateFlow(key, initialValue)
  }

  fun <T : Any> listenForArguments(
    key: String,
    initialValue: T?
  ): Flow<T> {
    return handle.getStateFlow(key, initialValue)
      .filterNotNull()
  }

}

class KurobaSavedStateViewModelFactory(
  owner: SavedStateRegistryOwner,
  defaultArgs: Bundle?
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(
    key: String,
    modelClass: Class<T>,
    handle: SavedStateHandle
  ): T {
    return SavedStateViewModel(handle) as T
  }
}
