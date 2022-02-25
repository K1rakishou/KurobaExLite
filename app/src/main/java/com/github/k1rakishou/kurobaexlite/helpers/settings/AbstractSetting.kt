package com.github.k1rakishou.kurobaexlite.helpers.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

abstract class AbstractSetting<T>(
  protected val dataStore: DataStore<Preferences>
) {
  protected val currentlyCached = MutableStateFlow<T?>(null)

  abstract val defaultValue: T
  abstract val settingKey: String

  abstract suspend fun read(): T
  abstract suspend fun write(value: T)
  abstract fun listen(): Flow<T>

  fun listenAsStateFlow(scope: CoroutineScope): StateFlow<T?> {
    return currentlyCached.map {
      if (it == null) {
        return@map read()
      }

      return@map it
    }.stateIn(scope, SharingStarted.Eagerly, null)
  }

}