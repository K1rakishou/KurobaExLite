package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

class ScreenSavedStateViewModel(
  private val handle: SavedStateHandle
) : ViewModel() {

  fun onNewArguments(newArgs: Bundle?) {
    if (newArgs == null) {
      handle.keys().forEach { key -> handle.remove<Any?>(key) }
      return
    }

    val keysToRemove = mutableSetOf<String>()
    val argsToSetOrUpdate = mutableMapOf<String, Any?>()

    val newArgKeys = newArgs.keySet()
    val oldArgKeys = handle.keys()

    val keysForIteration = newArgKeys + oldArgKeys

    keysForIteration.forEach { key ->
      // Old argument key is not present in new arguments, so we need to remove it
      if (oldArgKeys.contains(key) && !newArgKeys.contains(key)) {
        keysToRemove += key
        return@forEach
      }

      // Argument key present in both old and new arguments, check whether the argument value is
      // different and only if it is update the SavedStateHandle
      if (oldArgKeys.contains(key) && newArgKeys.contains(key)) {
        if (handle.get<Any?>(key) != newArgs.get(key)) {
          argsToSetOrUpdate[key] = newArgs.get(key)
        }

        return@forEach
      }

      // Argument key was added in new arguments
      if (!oldArgKeys.contains(key) && newArgKeys.contains(key)) {
        argsToSetOrUpdate[key] = newArgs.get(key)
        return@forEach
      }
    }

    keysToRemove.forEach { key ->
      handle.remove<Any?>(key)
    }

    argsToSetOrUpdate.entries.forEach { (key, value) ->
      handle.set(key, value)
    }
  }

  fun removeSavedStateHandle() {
    handle.keys().forEach { key -> handle.remove<Any?>(key) }
  }

  fun <T> setArgument(key: String, value: T) {
    handle.set(key, value)
  }

  fun <T : Any?> saveable(
    key: String,
    saver: Saver<T, out Any>,
    init: () -> T,
  ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> {
    return PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> { _, _ ->
      val value = handle.customSaveable<T>(
        key = key,
        saver = saver,
        init = { init() }
      )

      val mutableState = mutableStateOf(value)

      // Create a property that delegates to the mutableState
      return@PropertyDelegateProvider object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
          return mutableState.getValue(thisRef, property)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
          mutableState.setValue(thisRef, property, value)
        }
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

  private fun <T : Any?> SavedStateHandle.customSaveable(
    key: String,
    saver: Saver<T, out Any> = autoSaver(),
    init: () -> T,
  ): T {
    @Suppress("UNCHECKED_CAST")
    saver as Saver<T, Any>

    @Suppress("DEPRECATION")
    val value = get<Any?>(key)?.let { valueOrBundle ->
      val value = if (valueOrBundle is Bundle) {
        valueOrBundle.get("value")
      } else {
        valueOrBundle
      }

      return@let value?.let(saver::restore)
    } ?: init()

    // Hook up saving the state to the SavedStateHandle
    setSavedStateProvider(key) {
      bundleOf("value" to with(saver) {
        SaverScope { SavedStateHandle.validateValue(value) }.save(value)
      })
    }

    return value
  }

  companion object {
    fun <T : Any?> saver(): Saver<T, Any> {
      return Saver<T, Any>(
        save = { it as Any },
        restore = { it as T }
      )
    }
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
    return ScreenSavedStateViewModel(handle) as T
  }
}
