package com.github.k1rakishou.kurobaexlite.helpers.html

import java.util.concurrent.ConcurrentHashMap

object HtmlUnescape {

  private val replacementMap = ConcurrentHashMap<Char, List<ReplacementInfo>>()

  init {
    replacementMap['g'] = listOf(
      ReplacementInfo(
        fullEscapedValue = "&gt;",
        replacement = ">"
      )
    )

    replacementMap['l'] = listOf(
      ReplacementInfo(
        fullEscapedValue = "&lt;",
        replacement = "<"
      )
    )

    replacementMap['q'] = listOf(
      ReplacementInfo(
        fullEscapedValue = "&quot;",
        replacement = "\""
      )
    )

    replacementMap['a'] = listOf(
      ReplacementInfo(
        fullEscapedValue = "&amp;",
        replacement = "&"
      )
    )
  }

  fun unescape(input: String): String {
    if (input.isEmpty()) {
      return ""
    }

    val stringBuilder = StringBuilder(input.length)
    var offset = 0

    while (offset < input.length) {
      val currentChar = input[offset]

      when (currentChar) {
        '&' -> {
          val processedCharacters = unescapeTextElement(stringBuilder, input, offset)
          if (processedCharacters > 0) {
            offset += processedCharacters
            continue
          }

          // fallthrough
        }
      }

      stringBuilder.append(currentChar)
      ++offset
    }

    return stringBuilder.toString()
  }

  private fun unescapeTextElement(
    stringBuilder: StringBuilder,
    input: String,
    offset: Int
  ): Int {
    val firstChar = input[offset]
    if (firstChar != '&') {
      return 0
    }

    val secondChar = input.getOrNull(offset + 1)
      ?: return 0

    when (secondChar) {
      '#' -> {
        return unescapeAsciiCharacter(input, offset, stringBuilder)
      }
      'g', 'l', 'q', 'a' -> {
        val replacementInfoList = replacementMap[secondChar]
        if (replacementInfoList == null || replacementInfoList.isEmpty()) {
          return 0
        }

        for (replacementInfo in replacementInfoList) {
          val equals = compareExactSafe(input, offset, replacementInfo.fullEscapedValue)
          if (equals) {
            stringBuilder.append(replacementInfo.replacement)
            return replacementInfo.fullEscapedValue.length
          }
        }

        return 0
      }
    }

    return 0
  }

  private fun unescapeAsciiCharacter(
    input: String,
    offset: Int,
    stringBuilder: StringBuilder
  ): Int {
    var localOffset = 2
    var endFound = false
    val asciiEncoded = StringBuilder(3)

    while (true) {
      val asciiPart = input.getOrNull(offset + localOffset)
        ?: break

      ++localOffset

      if (!asciiPart.isDigit()) {
        if (asciiPart == ';') {
          endFound = true
        }

        break
      }

      asciiEncoded.append(asciiPart)
    }

    if (!endFound) {
      return 0
    }

    if (asciiEncoded.length != 3) {
      return 0
    }

    val asciiChar = asciiEncoded.toString().toIntOrNull()?.toChar()
      ?: return 0

    stringBuilder.append(asciiChar)

    return localOffset
  }

  private fun compareExactSafe(
    string: String,
    offset: Int,
    text: String
  ): Boolean {
    for ((index, ch1) in text.withIndex()) {
      val ch2 =  string.getOrNull(offset + index)
        ?: return false

      if (ch1 != ch2) {
        return false
      }
    }

    return true
  }

  class ReplacementInfo(
    val fullEscapedValue: String,
    val replacement: String
  )

}