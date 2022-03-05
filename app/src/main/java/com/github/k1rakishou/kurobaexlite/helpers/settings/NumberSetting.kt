package com.github.k1rakishou.kurobaexlite.helpers.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import logcat.logcat

class NumberSetting<T : Number>(
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
        val value = (prefs.get(prefsKey)?.deserializeToNumber() as T?)
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
    _valueFlow.value = readValue

    return readValue
  }

  override suspend fun write(value: T) {
    if (cachedValue == value) {
      return
    }

    val numberAsString = value.serializeToString()
      ?: return

    dataStore.edit { prefs ->
      prefs.set(prefsKey, numberAsString)
      cachedValue = value
      _valueFlow.value = value
    }
  }

  override fun listen(): Flow<T> {
    return dataStore.data
      .map { prefs -> (prefs.get(prefsKey)?.deserializeToNumber() as T?) ?: read() }
  }

  private fun Number.serializeToString(): String? {
    val type = getType()
    if (type == null) {
      logcat { "serializeToString() Unknown number type (number class=${this.javaClass}, value=${this})" }
      return null
    }

    val value = toString()
    return "${type};${value}"
  }

  private fun String.deserializeToNumber(): Number? {
    val parts = split(";")
    if (parts.size != 2) {
      logcat { "deserializeToNumber() Invalid input, does not contain the \';\' separator (value=${this})" }
      return null
    }

    val numberType = parts.getOrNull(0)?.toIntOrNull()
    if (numberType == null) {
      logcat { "deserializeToNumber() Failed to extract numberType (value=${this})" }
      return null
    }

    val valueString = parts.getOrNull(1)
    if (valueString == null) {
      logcat { "deserializeToNumber() Failed to extract valueString (value=${this})" }
      return null
    }

    val value: Number? = when (numberType) {
      DoubleType -> valueString.toDoubleOrNull()
      FloatType -> valueString.toFloatOrNull()
      LongType -> valueString.toLongOrNull()
      IntType -> valueString.toIntOrNull()
      ShortType -> valueString.toShortOrNull()
      ByteType -> valueString.toByteOrNull()
      else -> {
        logcat { "deserializeToNumber() Unknown numberType: ${numberType}" }
        return null
      }
    }

    if (value == null) {
      logcat { "deserializeToNumber() Failed to deserialize \'$valueString\' as ${numberType}" }
      return null
    }

    return value
  }

  private fun Number.getType(): Int? {
    return when (this) {
      is Double -> DoubleType
      is Float -> FloatType
      is Long -> LongType
      is Int -> IntType
      is Short -> ShortType
      is Byte -> ByteType
      else -> {
        logcat { "getType() Unknown Number class: ${this.javaClass.simpleName}, value: ${this}" }
        null
      }
    }
  }

  companion object {
    private const val DoubleType = 0
    private const val FloatType = 1
    private const val LongType = 2
    private const val IntType = 3
    private const val ShortType = 4
    private const val ByteType = 5
  }

}