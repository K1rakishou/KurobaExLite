package com.github.k1rakishou.kurobaexlite.helpers.settings.impl

import androidx.annotation.GuardedBy
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MapSetting<K, V>(
  private val moshi: Moshi,
  private val mapperTo: (KeyValue<K, V>) -> MapSettingEntry,
  private val mapperFrom: (MapSettingEntry) -> KeyValue<K, V>,
  override val defaultValue: Map<K, V> = emptyMap(),
  override val settingKey: String,
  dataStore: DataStore<Preferences>
) : AbstractSetting<Map<K, V>>(dataStore) {
  private val mutex = Mutex()

  @Volatile
  @GuardedBy("mutex")
  private var cache: MutableMap<K, V>? = null

  private val prefsKey: Preferences.Key<String> = stringPreferencesKey(settingKey)

  private suspend fun <T : Any?> withCache(func: MutableMap<K, V>.() -> T): T {
    val _cache = if (cache != null) {
      cache!!
    } else {
      val cacheData = read()

      mutex.withLock {
        cache = cacheData.toMutableMap()
        return@withLock cache!!
      }
    }

    return func(_cache)
  }

  suspend fun put(key: K, value: V) {
    val cacheCopy = withCache {
      put(key, value)
      return@withCache toMap()
    }

    write(cacheCopy)
  }

  suspend fun get(key: K): V? {
    return withCache { get(key) }
  }

  suspend fun remove(key: K): V? {
    val (value, cacheCopy) = withCache {
      val value = remove(key)
      return@withCache value to toMap()
    }

    write(cacheCopy)
    return value
  }

  override suspend fun read(): Map<K, V> {
    val cached = cachedValue
    if (cached != null) {
      return cached
    }

    val readValue = dataStore.data
      .map { prefs ->
        val mapSettingEntries = moshi
          .adapter<MapSettingEntries>(MapSettingEntries::class.java)
          .fromJson(prefs.get(prefsKey) ?: "")

        if (mapSettingEntries == null) {
          return@map emptyMap<K, V>()
        }

        val resultMap = mutableMapWithCap<K, V>(mapSettingEntries.entries.size)

        mapSettingEntries.entries.forEach { mapSettingEntry ->
          val mapped = mapperFrom(mapSettingEntry)
          resultMap[mapped.key] = mapped.value
        }

        return@map resultMap
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

  override suspend fun write(value: Map<K, V>) {
    if (cachedValue == value) {
      return
    }

    dataStore.edit { prefs ->
      val entries = mutableListWithCap<MapSettingEntry>(value.size)

      value.entries.forEach { entry ->
        entries += mapperTo(KeyValue(entry.key, entry.value))
      }

      val json = moshi
        .adapter<MapSettingEntries>(MapSettingEntries::class.java)
        .toJson(MapSettingEntries(entries))

      prefs.set(prefsKey, json)
      cachedValue = value
    }
  }

  override suspend fun remove() {
    cachedValue = null
    dataStore.edit { prefs -> prefs.remove(prefsKey) }
    withCache { clear() }
  }

  override fun listen(): Flow<Map<K, V>> {
    return dataStore.data
      .map { prefs ->
        val mapSettingEntries = moshi
          .adapter<MapSettingEntries>(MapSettingEntries::class.java)
          .fromJson(prefs.get(prefsKey) ?: "")

        if (mapSettingEntries == null) {
          return@map emptyMap<K, V>()
        }

        val resultMap = mutableMapWithCap<K, V>(mapSettingEntries.entries.size)

        mapSettingEntries.entries.forEach { mapSettingEntry ->
          val mapped = mapperFrom(mapSettingEntry)
          resultMap[mapped.key] = mapped.value
        }

        return@map resultMap
      }.catch {
        write(defaultValue)
        emit(defaultValue)
      }
  }
}

@JsonClass(generateAdapter = true)
data class MapSettingEntries(
  @Json(name = "entries") val entries: List<MapSettingEntry>
)

@JsonClass(generateAdapter = true)
data class MapSettingEntry(
  @Json(name = "key") val key: String,
  @Json(name = "value") val value: String
)

data class KeyValue<K, V>(
  val key: K,
  val value: V
)