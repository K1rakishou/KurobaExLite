package com.github.k1rakishou.kurobaexlite.helpers.settings.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class JsonSetting<T : Any?>(
  val jsonAdapter: JsonAdapter<T>,
  override val defaultValue: T,
  override val settingKey: String,
  dataStore: DataStore<Preferences>
) : AbstractSetting<T>(dataStore) {
  private val prefsKey: Preferences.Key<String> = stringPreferencesKey(settingKey)

  override suspend fun read(): T {
    val cached = cachedValue
    if (cached != null) {
      return cached
    }

    val readValue = dataStore.data
      .map { prefs ->
        val value = prefs.get(prefsKey)?.let { json -> fromJson(json) }
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

  override suspend fun write(value: T) {
    if (cachedValue == value) {
      return
    }

    dataStore.edit { prefs ->
      prefs.set(prefsKey, toJson(value))
      cachedValue = value
    }
  }

  override suspend fun remove() {
    cachedValue = null
    dataStore.edit { prefs -> prefs.remove(prefsKey) }
  }

  override fun listen(): Flow<T> {
    return dataStore.data
      .map { prefs ->
        return@map prefs.get(prefsKey)
          ?.let { fromJson(it) }
          ?: read()
      }
      .catch {
        write(defaultValue)
        emit(defaultValue)
      }
  }

  private fun toJson(value: T): String {
    return jsonAdapter.toJson(value)
  }

  private fun fromJson(json: String): T {
    return try {
      jsonAdapter.fromJson(json) ?: defaultValue
    } catch (error: Throwable) {
      logcatError { "jsonAdapter.fromJson() error.\njson=\'$json\',\nerror=${error}" }
      defaultValue
    }
  }

}