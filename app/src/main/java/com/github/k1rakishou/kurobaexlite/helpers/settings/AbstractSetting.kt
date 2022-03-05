package com.github.k1rakishou.kurobaexlite.helpers.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class AbstractSetting<T>(
  protected val dataStore: DataStore<Preferences>
) {
  @Volatile protected var cachedValue: T? = null

  protected val _valueFlow = MutableStateFlow<T>(defaultValue)
  val valueFlow: StateFlow<T>
    get() = _valueFlow.asStateFlow()

  abstract val defaultValue: T
  abstract val settingKey: String

  abstract suspend fun read(): T
  abstract suspend fun write(value: T)
  abstract fun listen(): Flow<T>

}