package com.zstream.android.data

import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.WatchPartyStatusRequest
import com.zstream.android.data.remote.WatchPartyRoomResponse
import com.zstream.android.data.remote.WatchPartyStatusResponse
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchPartyRepository @Inject constructor(
    private val api: BackendApi,
    private val accountRepo: AccountRepository
) {
    /**
     * Send current player status to the backend.
     */
    suspend fun sendStatus(request: WatchPartyStatusRequest): Result<WatchPartyStatusResponse> {
        return try {
            val session = accountRepo.session.first()
            val authHeader = session?.let { "Bearer ${it.token}" }
            val response = api.sendPlayerStatus(authHeader, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get statuses of all users in a specific room.
     */
    suspend fun getRoomStatuses(roomCode: String): Result<WatchPartyRoomResponse> {
        return try {
            val session = accountRepo.session.first()
            val authHeader = session?.let { "Bearer ${it.token}" }
            val response = api.getRoomStatuses(authHeader, roomCode)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Helper to get current userId from account repository session flow
     */
    suspend fun getUserId(): String? {
        return accountRepo.session.first()?.userId
    }
}
