package com.github.k1rakishou.kurobaexlite.helpers

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

fun <K : Parcelable, V : Parcelable> Bundle.putParcelableMap(key: String, value: Map<K, V>) {
  val listOfParcelablePairs = value.entries.map { ParcelablePair(it.key, it.value) }.toList()
  val arrayListOfParcelablePairs = ArrayList(listOfParcelablePairs)

  putParcelableArrayList(key, arrayListOfParcelablePairs)
}

inline fun <reified K : Parcelable, reified V : Parcelable> Bundle.getParcelableMap(key: String): Map<K, V> {
  val arrayListOfParcelablePairs = getParcelableArrayList<ParcelablePair>(key)
    ?: return emptyMap()

  val resultMap = mutableMapOf<K, V>()

  arrayListOfParcelablePairs.forEach { parcelablePair ->
    check(parcelablePair.first is K) { "parcelablePair.first is not \'${K::class.java}\'" }
    check(parcelablePair.second is V) { "parcelablePair.second is not \'${V::class.java}\'" }

    resultMap[parcelablePair.first] = parcelablePair.second
  }

  return resultMap
}

fun <K, V> Bundle.putSerializableMap(key: String, map: Map<K, V>, mapper: (K, V) -> Bundle) {
  val arrayListOfPairs = ArrayList(map.entries.map { (key, value) -> mapper(key, value) })
  putParcelableArrayList(key, arrayListOfPairs)
}

fun <K, V> Bundle.getSerializableMap(key: String, mapper: (Bundle) -> Pair<K, V>): Map<K, V> {
  val parcelableArrayList = getParcelableArrayList<Bundle>(key)
    ?: return emptyMap()

  val resultMap = mutableMapOf<K, V>()

  parcelableArrayList
    .map { bundle -> mapper(bundle) }
    .forEach { (key, value) -> resultMap[key] = value }

  return resultMap
}

fun Parcel.readBooleanKt(): Boolean {
  return readInt() == 1
}

fun Parcel.writeBooleanKt(value: Boolean) {
  writeInt(if (value) 1 else 0)
}

@Parcelize
data class ParcelablePair(
  val first: Parcelable,
  val second: Parcelable
) : Parcelable