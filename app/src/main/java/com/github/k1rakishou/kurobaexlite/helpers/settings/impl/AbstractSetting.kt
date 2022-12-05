package com.github.k1rakishou.kurobaexlite.helpers.settings.impl

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

  /**
   * When [eagerly] is true, a value will be emitted right after subscribing to the flow, otherwise it will be skipped and
   * only the actual updates will be emitted
   * */
  abstract fun listen(eagerly: Boolean = true): Flow<T>

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AbstractSetting<*>

    if (settingKey != other.settingKey) return false

    return true
  }

  override fun hashCode(): Int {
    return settingKey.hashCode()
  }

}