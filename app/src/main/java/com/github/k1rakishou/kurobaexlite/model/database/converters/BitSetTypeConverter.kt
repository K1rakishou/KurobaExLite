package com.github.k1rakishou.kurobaexlite.model.database.converters

import androidx.room.TypeConverter
import java.util.BitSet

class BitSetTypeConverter {

  @TypeConverter
  fun toBitSet(value: Long): BitSet {
    return BitSet.valueOf(longArrayOf(value))
  }

  @TypeConverter
  fun fromBitSet(bitSet: BitSet): Long {
    val array = bitSet.toLongArray()
    if (array.isEmpty()) {
      return 0L
    }

    return array.first()
  }

}