package com.github.k1rakishou.kurobaexlite.model.data.local

interface LoginDetails

interface LoginResult

data class Chan4LoginDetails(
  val token: String,
  val pin: String
) : LoginDetails

data class Chan4LoginResult(
  val passcodeCookie: String
) : LoginResult

data class DvachLoginDetails(
  val passcode: String
)

data class DvachLoginResult(
  val passcodeCookie: String
) : LoginResult