package com.github.k1rakishou.kurobaexlite.helpers.settings.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class StringSetting(
  override val defaultValue: String,
  override val settingKey: String,
  dataStore: DataStore<Preferences>
) : AbstractSetting<String>(dataStore) {
  private val prefsKey: Preferences.Key<String> = stringPreferencesKey(settingKey)

  override suspend fun read(): String {
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

  override suspend fun write(value: String) {
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

  override fun listen(eagerly: Boolean): Flow<String> {
    return dataStore.data
      .let { flow ->
        return@let if (eagerly) {
          flow
        } else {
          flow.drop(1)
        }
      }
      .map { prefs -> prefs.get(prefsKey) ?: read() }
      .catch {
        write(defaultValue)
        emit(defaultValue)
      }
  }

}