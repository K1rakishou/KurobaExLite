package com.github.k1rakishou.kurobaexlite.helpers.html

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.k1rakishou.kurobaexlite.helpers.util.groupOrNull
import java.util.*
import java.util.regex.Pattern

class StaticHtmlColorRepository {

  private val map = mutableMapOf<String, Int>()

  init {
    map["aliceblue"] = 0xFFF0F8FFL.toInt()
    map["antiquewhite"] = 0xFFFAEBD7L.toInt()
    map["aqua"] = 0xFF00FFFFL.toInt()
    map["aquamarine"] = 0xFF7FFFD4L.toInt()
    map["azure"] = 0xFFF0FFFFL.toInt()
    map["beige"] = 0xFFF5F5DCL.toInt()
    map["bisque"] = 0xFFFFE4C4L.toInt()
    map["black"] = 0xFF000000L.toInt()
    map["blanchedalmond"] = 0xFFFFEBCDL.toInt()
    map["blue"] = 0xFF0000FFL.toInt()
    map["blueviolet"] = 0xFF8A2BE2L.toInt()
    map["brown"] = 0xFFA52A2AL.toInt()
    map["burlywood"] = 0xFFDEB887L.toInt()
    map["cadetblue"] = 0xFF5F9EA0L.toInt()
    map["chartreuse"] = 0xFF7FFF00L.toInt()
    map["chocolate"] = 0xFFD2691EL.toInt()
    map["coral"] = 0xFFFF7F50L.toInt()
    map["cornflowerblue"] = 0xFF6495EDL.toInt()
    map["cornsilk"] = 0xFFFFF8DCL.toInt()
    map["crimson"] = 0xFFDC143CL.toInt()
    map["cyan"] = 0xFF00FFFFL.toInt()
    map["darkblue"] = 0xFF00008BL.toInt()
    map["darkcyan"] = 0xFF008B8BL.toInt()
    map["darkgoldenrod"] = 0xFFB8860BL.toInt()
    map["darkgray"] = 0xFFA9A9A9L.toInt()
    map["darkgrey"] = 0xFFA9A9A9L.toInt()
    map["darkgreen"] = 0xFF006400L.toInt()
    map["darkkhaki"] = 0xFFBDB76BL.toInt()
    map["darkmagenta"] = 0xFF8B008BL.toInt()
    map["darkolivegreen"] = 0xFF556B2FL.toInt()
    map["darkorange"] = 0xFFFF8C00L.toInt()
    map["darkorchid"] = 0xFF9932CCL.toInt()
    map["darkred"] = 0xFF8B0000L.toInt()
    map["darksalmon"] = 0xFFE9967AL.toInt()
    map["darkseagreen"] = 0xFF8FBC8FL.toInt()
    map["darkslateblue"] = 0xFF483D8BL.toInt()
    map["darkslategray"] = 0xFF2F4F4FL.toInt()
    map["darkslategrey"] = 0xFF2F4F4FL.toInt()
    map["darkturquoise"] = 0xFF00CED1L.toInt()
    map["darkviolet"] = 0xFF9400D3L.toInt()
    map["deeppink"] = 0xFFFF1493L.toInt()
    map["deepskyblue"] = 0xFF00BFFFL.toInt()
    map["dimgray"] = 0xFF696969L.toInt()
    map["dimgrey"] = 0xFF696969L.toInt()
    map["dodgerblue"] = 0xFF1E90FFL.toInt()
    map["firebrick"] = 0xFFB22222L.toInt()
    map["floralwhite"] = 0xFFFFFAF0L.toInt()
    map["forestgreen"] = 0xFF228B22L.toInt()
    map["fuchsia"] = 0xFFFF00FFL.toInt()
    map["gainsboro"] = 0xFFDCDCDCL.toInt()
    map["ghostwhite"] = 0xFFF8F8FFL.toInt()
    map["gold"] = 0xFFFFD700L.toInt()
    map["goldenrod"] = 0xFFDAA520L.toInt()
    map["gray"] = 0xFF808080L.toInt()
    map["grey"] = 0xFF808080L.toInt()
    map["green"] = 0xFF008000L.toInt()
    map["greenyellow"] = 0xFFADFF2FL.toInt()
    map["honeydew"] = 0xFFF0FFF0L.toInt()
    map["hotpink"] = 0xFFFF69B4L.toInt()
    map["indianred"] = 0xFFCD5C5CL.toInt()
    map["indigo"] = 0xFF4B0082L.toInt()
    map["ivory"] = 0xFFFFFFF0L.toInt()
    map["khaki"] = 0xFFF0E68CL.toInt()
    map["lavender"] = 0xFFE6E6FAL.toInt()
    map["lavenderblush"] = 0xFFFFF0F5L.toInt()
    map["lawngreen"] = 0xFF7CFC00L.toInt()
    map["lemonchiffon"] = 0xFFFFFACDL.toInt()
    map["lightblue"] = 0xFFADD8E6L.toInt()
    map["lightcoral"] = 0xFFF08080L.toInt()
    map["lightcyan"] = 0xFFE0FFFFL.toInt()
    map["lightgoldenrodyellow"] = 0xFFFAFAD2L.toInt()
    map["lightgray"] = 0xFFD3D3D3L.toInt()
    map["lightgrey"] = 0xFFD3D3D3L.toInt()
    map["lightgreen"] = 0xFF90EE90L.toInt()
    map["lightpink"] = 0xFFFFB6C1L.toInt()
    map["lightsalmon"] = 0xFFFFA07AL.toInt()
    map["lightseagreen"] = 0xFF20B2AAL.toInt()
    map["lightskyblue"] = 0xFF87CEFAL.toInt()
    map["lightslategray"] = 0xFF778899L.toInt()
    map["lightslategrey"] = 0xFF778899L.toInt()
    map["lightsteelblue"] = 0xFFB0C4DEL.toInt()
    map["lightyellow"] = 0xFFFFFFE0L.toInt()
    map["lime"] = 0xFF00FF00L.toInt()
    map["limegreen"] = 0xFF32CD32L.toInt()
    map["linen"] = 0xFFFAF0E6L.toInt()
    map["magenta"] = 0xFFFF00FFL.toInt()
    map["maroon"] = 0xFF800000L.toInt()
    map["mediumaquamarine"] = 0xFF66CDAAL.toInt()
    map["mediumblue"] = 0xFF0000CDL.toInt()
    map["mediumorchid"] = 0xFFBA55D3L.toInt()
    map["mediumpurple"] = 0xFF9370DBL.toInt()
    map["mediumseagreen"] = 0xFF3CB371L.toInt()
    map["mediumslateblue"] = 0xFF7B68EEL.toInt()
    map["mediumspringgreen"] = 0xFF00FA9AL.toInt()
    map["mediumturquoise"] = 0xFF48D1CCL.toInt()
    map["mediumvioletred"] = 0xFFC71585L.toInt()
    map["midnightblue"] = 0xFF191970L.toInt()
    map["mintcream"] = 0xFFF5FFFAL.toInt()
    map["mistyrose"] = 0xFFFFE4E1L.toInt()
    map["moccasin"] = 0xFFFFE4B5L.toInt()
    map["navajowhite"] = 0xFFFFDEADL.toInt()
    map["navy"] = 0xFF000080L.toInt()
    map["oldlace"] = 0xFFFDF5E6L.toInt()
    map["olive"] = 0xFF808000L.toInt()
    map["olivedrab"] = 0xFF6B8E23L.toInt()
    map["orange"] = 0xFFFFA500L.toInt()
    map["orangered"] = 0xFFFF4500L.toInt()
    map["orchid"] = 0xFFDA70D6L.toInt()
    map["palegoldenrod"] = 0xFFEEE8AAL.toInt()
    map["palegreen"] = 0xFF98FB98L.toInt()
    map["paleturquoise"] = 0xFFAFEEEEL.toInt()
    map["palevioletred"] = 0xFFDB7093L.toInt()
    map["papayawhip"] = 0xFFFFEFD5L.toInt()
    map["peachpuff"] = 0xFFFFDAB9L.toInt()
    map["peru"] = 0xFFCD853FL.toInt()
    map["pink"] = 0xFFFFC0CBL.toInt()
    map["plum"] = 0xFFDDA0DDL.toInt()
    map["powderblue"] = 0xFFB0E0E6L.toInt()
    map["purple"] = 0xFF800080L.toInt()
    map["rebeccapurple"] = 0xFF663399L.toInt()
    map["red"] = 0xFFFF0000L.toInt()
    map["rosybrown"] = 0xFFBC8F8FL.toInt()
    map["royalblue"] = 0xFF4169E1L.toInt()
    map["saddlebrown"] = 0xFF8B4513L.toInt()
    map["salmon"] = 0xFFFA8072L.toInt()
    map["sandybrown"] = 0xFFF4A460L.toInt()
    map["seagreen"] = 0xFF2E8B57L.toInt()
    map["seashell"] = 0xFFFFF5EEL.toInt()
    map["sienna"] = 0xFFA0522DL.toInt()
    map["silver"] = 0xFFC0C0C0L.toInt()
    map["skyblue"] = 0xFF87CEEBL.toInt()
    map["slateblue"] = 0xFF6A5ACDL.toInt()
    map["slategray"] = 0xFF708090L.toInt()
    map["slategrey"] = 0xFF708090L.toInt()
    map["snow"] = 0xFFFFFAFAL.toInt()
    map["springgreen"] = 0xFF00FF7FL.toInt()
    map["steelblue"] = 0xFF4682B4L.toInt()
    map["tan"] = 0xFFD2B48CL.toInt()
    map["teal"] = 0xFF008080L.toInt()
    map["thistle"] = 0xFFD8BFD8L.toInt()
    map["tomato"] = 0xFFFF6347L.toInt()
    map["turquoise"] = 0xFF40E0D0L.toInt()
    map["violet"] = 0xFFEE82EEL.toInt()
    map["wheat"] = 0xFFF5DEB3L.toInt()
    map["white"] = 0xFFFFFFFFL.toInt()
    map["whitesmoke"] = 0xFFF5F5F5L.toInt()
    map["yellow"] = 0xFFFFFF00L.toInt()
    map["yellowgreen"] = 0xFF9ACD32L.toInt()
  }

  fun parseColor(colorRaw: String): Int? {
    if (colorRaw.startsWith("rgb(", ignoreCase = true)) {
      return parseRbgColor(colorRaw.drop(4))
    }

    if (colorRaw.startsWith("#")) {
      return android.graphics.Color.parseColor(colorRaw)
    }

    return map[colorRaw.lowercase(Locale.ENGLISH)]
  }

  private fun parseRbgColor(colorRaw: String): Int? {
    val matcher = RGB_COLOR_PATTERN.matcher(colorRaw)
    if (!matcher.find()) {
      return null
    }

    val r = matcher.groupOrNull(1)?.toIntOrNull() ?: return null
    val g = matcher.groupOrNull(2)?.toIntOrNull() ?: return null
    val b = matcher.groupOrNull(3)?.toIntOrNull() ?: return null

    return Color(r, g, b).toArgb()
  }

  companion object {
    private val RGB_COLOR_PATTERN = Pattern.compile("(\\d+)\\D+(\\d+)\\D+(\\d+)")
  }

}