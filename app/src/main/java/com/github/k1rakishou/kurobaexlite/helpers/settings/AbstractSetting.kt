package com.github.k1rakishou.kurobaexlite.helpers.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

abstract class AbstractSetting<T>(
  protected val dataStore: DataStore<Preferences>
) {
  @Volatile protected var cachedValue: T? = null

  abstract val defaultValue: T
  abstract val settingKey: String

  abstract suspend fun read(): T
  abstract suspend fun write(value: T)
  abstract suspend fun remove()
  abstract fun listen(): Flow<T>

}