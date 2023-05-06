package com.github.k1rakishou.kpnc.model.repository

interface PostRepository {
  suspend fun watchPost(postUrl: String): Result<Boolean>
  suspend fun unwatchPost(postUrl: String): Result<Boolean>
}