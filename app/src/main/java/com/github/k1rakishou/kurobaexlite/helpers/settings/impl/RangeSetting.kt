package com.github.k1rakishou.kurobaexlite.helpers.settings.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class RangeSetting(
  val minValue: Int = Int.MIN_VALUE,
  val maxValue: Int = Int.MAX_VALUE,
  override val defaultValue: Int,
  override val settingKey: String,
  dataStore: DataStore<Preferences>
) : AbstractSetting<Int>(dataStore) {
  private val prefsKey: Preferences.Key<Int> = intPreferencesKey(settingKey)

  init {
    require(defaultValue in minValue..maxValue) {
      "Bad defaultValue: ${defaultValue} must be within ${minValue}..${maxValue} bounds"
    }
  }

  override suspend fun read(): Int {
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

        return@map value?.coerceIn(minValue, maxValue)
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

  override suspend fun write(value: Int) {
    if (cachedValue == value) {
      return
    }

    dataStore.edit { prefs ->
      prefs.set(prefsKey, value.coerceIn(minValue, maxValue))
      cachedValue = value
    }
  }

  override suspend fun remove() {
    cachedValue = null
    dataStore.edit { prefs -> prefs.remove(prefsKey) }
  }

  override fun listen(eagerly: Boolean): Flow<Int> {
    return dataStore.data
      .let { flow ->
        return@let if (eagerly) {
          flow
        } else {
          flow.drop(1)
        }
      }
      .map { prefs ->
        val value = (prefs.get(prefsKey)) ?: read()
        return@map value.coerceIn(minValue, maxValue)
      }
      .catch {
        write(defaultValue)
        emit(defaultValue)
      }
  }

}