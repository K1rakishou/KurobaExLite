package com.github.k1rakishou.kurobaexlite.helpers

import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4BoardFlagJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4BoardFlagsJson
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.ToJson

class Chan4BoardFlagsJsonAdapter {

  @FromJson
  fun fromJson(jsonReader: JsonReader): Chan4BoardFlagsJson {
    val resultList = mutableListOf<Chan4BoardFlagJson>()

    jsonReader.beginObject()

    while (jsonReader.hasNext()) {
      val key = jsonReader.nextName()
      val name = jsonReader.nextString()

      resultList += Chan4BoardFlagJson(key, name)
    }

    jsonReader.endObject()

    return Chan4BoardFlagsJson(resultList)
  }

  @ToJson
  fun toJson(boardFlagsJson: Chan4BoardFlagsJson): String {
    unreachable("Not used")
  }

}