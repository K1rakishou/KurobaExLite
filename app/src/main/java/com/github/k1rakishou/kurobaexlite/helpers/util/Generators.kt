package com.github.k1rakishou.kurobaexlite.helpers.util

import java.security.SecureRandom

object Generators {
    private const val BOUNDARY_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val random = SecureRandom.getInstance("SHA1PRNG")

    fun generateRandomHexString(symbolsCount: Int): String {
        require(symbolsCount > 0) { "Bad symbolsCount: ${symbolsCount}" }
        return ConversionUtils.bytesToHex(random.generateSeed(symbolsCount))
    }

    fun generateHttpBoundary(): String {
        val boundary = StringBuilder()

        for (i in 0 until 16) {
            val index = random.nextInt(BOUNDARY_CHARS.length)
            boundary.append(BOUNDARY_CHARS[index])
        }

        if (boundary[0].isLowerCase()) {
            boundary[0] = boundary[0].uppercaseChar()
        }

        return boundary.toString()
    }

}