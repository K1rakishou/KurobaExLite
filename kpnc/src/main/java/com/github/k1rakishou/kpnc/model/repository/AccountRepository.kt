package com.github.k1rakishou.kpnc.model.repository

import com.github.k1rakishou.kpnc.model.data.network.AccountInfoResponse

interface AccountRepository {
  suspend fun getAccountInfo(instanceAddress: String, userId: String): Result<AccountInfoResponse>
}