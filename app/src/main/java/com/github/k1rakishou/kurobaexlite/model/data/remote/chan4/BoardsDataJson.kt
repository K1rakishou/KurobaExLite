package com.github.k1rakishou.kurobaexlite.model.data.remote.chan4

import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.ToJson

@JsonClass(generateAdapter = true)
data class BoardsDataJson(
  @Json(name = "boards") val boards: List<BoardDataJson>
)

@JsonClass(generateAdapter = true)
data class BoardDataJson(
  @Json(name = "board") val boardCode: String?,
  @Json(name = "title") val boardTitle: String?,
  @Json(name = "meta_description") val boardDescription: String?,
  @Json(name = "ws_board") val workSafe: Int?,
  @Json(name = "board_flags") val boardFlags: BoardFlagsJson?
)

@JsonClass(generateAdapter = false)
data class BoardFlagsJson(
  val list: List<BoardFlagJson>
)

@JsonClass(generateAdapter = false)
data class BoardFlagJson(
  val key: String,
  val name: String
)

class BoardFlagsJsonAdapter {

  @FromJson
  fun fromJson(jsonReader: JsonReader): BoardFlagsJson {
    val resultList = mutableListOf<BoardFlagJson>()

    jsonReader.beginObject()

    while (jsonReader.hasNext()) {
      val key = jsonReader.nextName()
      val name = jsonReader.nextString()

      resultList += BoardFlagJson(key, name)
    }

    jsonReader.endObject()

    return BoardFlagsJson(resultList)
  }

  @ToJson
  fun toJson(boardFlagsJson: BoardFlagsJson): String {
    unreachable("Not used")
  }

}