package com.github.k1rakishou.kurobaexlite.helpers.settings.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class BooleanSetting(
  override val defaultValue: Boolean,
  override val settingKey: String,
  dataStore: DataStore<Preferences>
) : AbstractSetting<Boolean>(dataStore) {
  private val prefsKey: Preferences.Key<Boolean> = booleanPreferencesKey(settingKey)

  override suspend fun read(): Boolean {
    val cached = cachedValue
    if (cached != null) {
      return cached
    }

    val readValue = dataStore.data
      .map { prefs ->
        val value = prefs.get(prefsKey)
        if (value == null) {
          write(defaultValue)
        }

        return@map value
      }
      .catch {
        write(defaultValue)
        emit(defaultValue)
      }
      .firstOrNull()
      ?: defaultValue

    cachedValue = readValue

    return readValue
  }

  override suspend fun write(value: Boolean) {
    if (cachedValue == value) {
      return
    }

    dataStore.edit { prefs ->
      prefs.set(prefsKey, value)
      cachedValue = value
    }
  }

  override suspend fun remove() {
    cachedValue = null
    dataStore.edit { prefs -> prefs.remove(prefsKey) }
  }

  suspend fun toggle(): Boolean {
    val oldValue = read()
    val newValue = !oldValue

    write(newValue)
    return newValue
  }

  override fun listen(): Flow<Boolean> {
    return dataStore.data
      .map { prefs -> prefs.get(prefsKey) ?: read() }
      .catch {
        write(defaultValue)
        emit(defaultValue)
      }
  }

}