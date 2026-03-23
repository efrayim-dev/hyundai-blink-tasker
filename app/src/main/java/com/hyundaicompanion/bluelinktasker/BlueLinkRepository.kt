package com.hyundaicompanion.bluelinktasker

import android.content.Context

class BlueLinkRepository(
    private val prefs: SecurePrefs,
    private val session: SessionData,
    private val api: BlueLinkApi,
) {

    fun loadSessionFromStorage() {
        session.accessToken = prefs.accessToken
        session.refreshToken = prefs.refreshToken
        session.tokenExpiresAtEpochSec = prefs.tokenExpiresAtEpochSec
    }

    private fun persistSession() {
        prefs.accessToken = session.accessToken
        prefs.refreshToken = session.refreshToken
        prefs.tokenExpiresAtEpochSec = session.tokenExpiresAtEpochSec
    }

    suspend fun ensureSession(): Result<Unit> {
        loadSessionFromStorage()
        val u = prefs.username ?: return Result.failure(IllegalStateException("Account not configured"))
        val pw = prefs.password ?: return Result.failure(IllegalStateException("Account not configured"))

        if (session.accessToken.isNullOrBlank()) {
            return api.login(u, pw).also { if (it.isSuccess) persistSession() }
        }
        if (api.shouldRefresh()) {
            val refreshed = api.refreshToken()
            if (refreshed.isSuccess) {
                persistSession()
                return Result.success(Unit)
            }
            prefs.clearSession()
            loadSessionFromStorage()
            return api.login(u, pw).also { if (it.isSuccess) persistSession() }
        }
        return Result.success(Unit)
    }

    suspend fun loginAndSave(username: String, password: String): Result<Unit> {
        prefs.username = username
        prefs.password = password
        loadSessionFromStorage()
        return api.login(username, password).also { if (it.isSuccess) persistSession() }
    }

    suspend fun fetchVehicles(): Result<List<VehicleInfo>> {
        val u = prefs.username ?: return Result.failure(IllegalStateException("No username"))
        ensureSession().getOrElse { return Result.failure(it) }
        return api.fetchVehicles(u)
    }

    fun saveVehicleSelection(v: VehicleInfo) {
        prefs.vehicleVin = v.vin
        prefs.vehicleRegId = v.regId
        prefs.vehicleGeneration = v.generation
        prefs.vehicleBrandIndicator = v.brandIndicator
        prefs.vehicleNickname = v.nickname
        prefs.engineType = if (v.engineType == VehicleInfo.EngineType.EV) "EV" else "ICE"
        prefs.setupComplete = true
    }

    fun currentVehicle(): VehicleInfo? {
        val vin = prefs.vehicleVin ?: return null
        val regId = prefs.vehicleRegId ?: return null
        val gen = prefs.vehicleGeneration ?: "1"
        val brand = prefs.vehicleBrandIndicator ?: "H"
        val engine = when (prefs.engineType) {
            "EV" -> VehicleInfo.EngineType.EV
            else -> VehicleInfo.EngineType.ICE
        }
        return VehicleInfo(
            vin = vin,
            nickname = prefs.vehicleNickname ?: vin,
            regId = regId,
            generation = gen,
            brandIndicator = brand,
            engineType = engine,
        )
    }

    suspend fun unlock(): Result<String> {
        val v = currentVehicle() ?: return Result.failure(IllegalStateException("No vehicle selected"))
        val u = prefs.username ?: return Result.failure(IllegalStateException("No username"))
        val pin = prefs.pin ?: return Result.failure(IllegalStateException("No PIN"))
        ensureSession().getOrElse { return Result.failure(it) }
        return api.unlock(u, pin, v)
    }

    suspend fun lock(): Result<String> {
        val v = currentVehicle() ?: return Result.failure(IllegalStateException("No vehicle selected"))
        val u = prefs.username ?: return Result.failure(IllegalStateException("No username"))
        val pin = prefs.pin ?: return Result.failure(IllegalStateException("No PIN"))
        ensureSession().getOrElse { return Result.failure(it) }
        return api.lock(u, pin, v)
    }

    suspend fun remoteStart(options: RemoteStartOptions = RemoteStartOptions.fromSecurePrefs(prefs)): Result<String> {
        val v = currentVehicle() ?: return Result.failure(IllegalStateException("No vehicle selected"))
        val u = prefs.username ?: return Result.failure(IllegalStateException("No username"))
        val pin = prefs.pin ?: return Result.failure(IllegalStateException("No PIN"))
        ensureSession().getOrElse { return Result.failure(it) }
        return api.remoteStart(u, pin, v, options)
    }

    suspend fun remoteStop(): Result<String> {
        val v = currentVehicle() ?: return Result.failure(IllegalStateException("No vehicle selected"))
        val u = prefs.username ?: return Result.failure(IllegalStateException("No username"))
        val pin = prefs.pin ?: return Result.failure(IllegalStateException("No PIN"))
        ensureSession().getOrElse { return Result.failure(it) }
        return api.remoteStop(u, pin, v)
    }

    companion object {
        fun create(context: Context): BlueLinkRepository {
            val session = SessionData()
            return BlueLinkRepository(
                SecurePrefs(context.applicationContext),
                session,
                BlueLinkApi(session = session),
            )
        }
    }
}
