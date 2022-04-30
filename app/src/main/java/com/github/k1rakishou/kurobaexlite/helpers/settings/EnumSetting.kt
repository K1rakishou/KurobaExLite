package com.github.k1rakishou.kurobaexlite.helpers.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class EnumSetting<T : Enum<T>>(
  override val defaultValue: T,
  override val settingKey: String,
  private val enumClazz: Class<T>,
  dataStore: DataStore<Preferences>
) : AbstractSetting<T>(dataStore) {
  private val enumValues by lazy { enumClazz.enumConstants }
  private val prefsKey: Preferences.Key<String> = stringPreferencesKey(settingKey)

  override suspend fun read(): T {
    val cached = cachedValue
    if (cached != null) {
      return cached
    }

    val readValue = dataStore.data
      .map { prefs ->
        val enumName = prefs.get(prefsKey)
        val enumValue = enumValues.firstOrNull { it.name == enumName }

        if (enumName == null || enumValue == null) {
          write(defaultValue)
        }

        return@map enumValue ?: defaultValue
      }
      .catch {
        write(defaultValue)
        emit(defaultValue)
      }
      .firstOrNull()
      ?: defaultValue

    cachedValue = readValue
    _valueFlow.value = readValue

    return readValue
  }

  override suspend fun write(value: T) {
    if (cachedValue == value) {
      return
    }

    dataStore.edit { prefs ->
      prefs.set(prefsKey, value.name)
      cachedValue = value
      _valueFlow.value = value
    }
  }

  override suspend fun remove() {
    cachedValue = null
    dataStore.edit { prefs -> prefs.remove(prefsKey) }
  }

  override fun listen(): Flow<T> {
    return dataStore.data.map { prefs ->
      val enumName = prefs.get(prefsKey)
      val enumValue = enumValues.firstOrNull { it.name == enumName }

      return@map enumValue ?: read()
    }
  }

}