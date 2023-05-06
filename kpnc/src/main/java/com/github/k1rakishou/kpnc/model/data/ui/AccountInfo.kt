package com.github.k1rakishou.kpnc.model.data.ui

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat

data class AccountInfo(
  val accountId: String,
  val isValid: Boolean,
  val validUntil: DateTime
) {

  fun asText(): String {
    val localValidUntil = validUntil.toLocalDateTime()

    if (isValid) {
      return "Account is valid. Valid until: ${formatter.print(localValidUntil)}"
    }

    return "Account is NOT valid. Valid until: ${formatter.print(localValidUntil)}"
  }

  companion object {
    private val formatter = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .appendLiteral(' ')
      .append(ISODateTimeFormat.hourMinuteSecond())
      .appendTimeZoneOffset(null, true, 2, 2)
      .toFormatter()
  }

}