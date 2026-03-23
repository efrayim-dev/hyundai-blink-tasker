package com.hyundaicompanion.bluelinktasker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SessionData(
    var accessToken: String? = null,
    var refreshToken: String? = null,
    var tokenExpiresAtEpochSec: Long = 0L,
)

class BlueLinkApi(
    private val env: BlueLinkEnvironment = BlueLinkEnvironment.hyundaiUs(),
    private val session: SessionData,
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun login(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("username", username)
                .put("password", password)
                .toString()
                .toRequestBody(JSON_MEDIA)
            val req = Request.Builder()
                .url("${env.baseUrl}/v2/ac/oauth/token")
                .post(body)
                .header("User-Agent", "PostmanRuntime/7.26.10")
                .header("client_id", env.clientId)
                .header("client_secret", env.clientSecret)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Login failed (${resp.code}): $text")
                }
                applyTokenResponse(JSONObject(text))
            }
        }
    }

    suspend fun refreshToken(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rt = session.refreshToken ?: error("No refresh token")
            val body = JSONObject()
                .put("refresh_token", rt)
                .toString()
                .toRequestBody(JSON_MEDIA)
            val req = Request.Builder()
                .url("${env.baseUrl}/v2/ac/oauth/token/refresh")
                .post(body)
                .header("User-Agent", "PostmanRuntime/7.26.10")
                .header("client_secret", env.clientSecret)
                .header("client_id", env.clientId)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Token refresh failed (${resp.code}): $text")
                }
                applyTokenResponse(JSONObject(text))
            }
        }
    }

    suspend fun fetchVehicles(username: String): Result<List<VehicleInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = session.accessToken ?: error("Not logged in")
            val req = Request.Builder()
                .url("${env.baseUrl}/ac/v2/enrollment/details/$username")
                .get()
                .header("access_token", token)
                .header("client_id", env.clientId)
                .header("Host", env.host)
                .header("User-Agent", "okhttp/3.12.0")
                .header("payloadGenerated", "20200226171938")
                .header("includeNonConnectedVehicles", "Y")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Enrollment failed (${resp.code}): $text")
                }
                parseVehicles(JSONObject(text))
            }
        }
    }

    suspend fun unlock(
        username: String,
        pin: String,
        vehicle: VehicleInfo,
    ): Result<String> = withContext(Dispatchers.IO) {
        doorCommand(username, pin, vehicle, onPath = "ac/v2/rcs/rdo/on", label = "Unlock")
    }

    suspend fun lock(
        username: String,
        pin: String,
        vehicle: VehicleInfo,
    ): Result<String> = withContext(Dispatchers.IO) {
        doorCommand(username, pin, vehicle, onPath = "ac/v2/rcs/rdo/off", label = "Lock")
    }

    private fun doorCommand(
        username: String,
        pin: String,
        vehicle: VehicleInfo,
        onPath: String,
        label: String,
    ): Result<String> {
        return runCatching {
            if (session.accessToken.isNullOrBlank()) error("Not authenticated")
            val form = FormBody.Builder()
                .add("userName", username)
                .add("vin", vehicle.vin)
                .build()
            val req = Request.Builder()
                .url("${env.baseUrl}/$onPath")
                .post(form)
                .headers(defaultVehicleHeaders(username, pin, vehicle))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("$label failed (${resp.code}): $text")
                }
                "$label OK"
            }
        }
    }

    suspend fun remoteStart(
        username: String,
        pin: String,
        vehicle: VehicleInfo,
        durationMinutes: Int = 10,
        temperatureF: Int = 70,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (session.accessToken.isNullOrBlank()) error("Not authenticated")
            val (path, bodyJson, useOffset4) = startPayload(username, vehicle, durationMinutes, temperatureF)
            val req = Request.Builder()
                .url("${env.baseUrl}/$path")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
                .apply {
                    val h = defaultVehicleHeaders(username, pin, vehicle).newBuilder()
                    if (useOffset4) {
                        h.removeAll("offset")
                        h.add("offset", "-4")
                    }
                    headers(h.build())
                }
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Remote start failed (${resp.code}): $text")
                }
                "Remote start OK"
            }
        }
    }

    suspend fun remoteStop(
        username: String,
        pin: String,
        vehicle: VehicleInfo,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (session.accessToken.isNullOrBlank()) error("Not authenticated")
            val h = defaultVehicleHeaders(username, pin, vehicle).newBuilder()
            h.removeAll("offset")
            h.add("offset", "-4")
            val req = Request.Builder()
                .url("${env.baseUrl}/ac/v2/rcs/rsc/stop")
                .post("".toRequestBody(null))
                .headers(h.build())
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Remote stop failed (${resp.code}): $text")
                }
                "Remote stop OK"
            }
        }
    }

    private fun startPayload(
        username: String,
        vehicle: VehicleInfo,
        durationMinutes: Int,
        temperatureF: Int,
    ): Triple<String, JSONObject, Boolean> {
        val gen2Ev = vehicle.engineType == VehicleInfo.EngineType.EV && vehicle.generation == "2"
        val path = if (vehicle.engineType == VehicleInfo.EngineType.EV) {
            "ac/v2/evc/fatc/start"
        } else {
            "ac/v2/rcs/rsc/start"
        }
        val body = JSONObject()
            .put("Ims", 0)
            .put("airCtrl", 0)
            .put(
                "airTemp",
                JSONObject()
                    .put("unit", 1)
                    .put("value", temperatureF.toString()),
            )
            .put("defrost", false)
            .put("heating1", 0)
            .put("username", username)
            .put("vin", vehicle.vin)
        if (!gen2Ev) {
            body.put("igniOnDuration", durationMinutes)
            body.put("seatHeaterVentInfo", JSONObject.NULL)
        }
        val useOffset4 = true
        return Triple(path, body, useOffset4)
    }

    /** Call from repository before vehicle APIs; refreshes if within 10s of expiry. */
    fun shouldRefresh(): Boolean {
        val now = System.currentTimeMillis() / 1000
        return session.refreshToken != null &&
            session.tokenExpiresAtEpochSec > 0 &&
            now >= session.tokenExpiresAtEpochSec - 10
    }

    private fun applyTokenResponse(json: JSONObject) {
        session.accessToken = json.getString("access_token")
        session.refreshToken = json.getString("refresh_token")
        val inc = parseExpiresInSeconds(json)
        session.tokenExpiresAtEpochSec = System.currentTimeMillis() / 1000 + inc
    }

    private fun parseExpiresInSeconds(json: JSONObject): Long {
        val raw = json.opt("expires_in") ?: return 3600L
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 3600L
            else -> 3600L
        }
    }

    private fun defaultVehicleHeaders(
        username: String,
        pin: String,
        vehicle: VehicleInfo,
    ) = okhttp3.Headers.Builder()
        .add("access_token", session.accessToken!!)
        .add("client_id", env.clientId)
        .add("Host", env.host)
        .add("User-Agent", "okhttp/3.12.0")
        .add("registrationId", vehicle.regId)
        .add("gen", vehicle.generation)
        .add("username", username)
        .add("vin", vehicle.vin)
        .add("APPCLOUD-VIN", vehicle.vin)
        .add("Language", "0")
        .add("to", "ISS")
        .add("encryptFlag", "false")
        .add("from", "SPA")
        .add("brandIndicator", vehicle.brandIndicator)
        .add("bluelinkservicepin", pin)
        .add("offset", "-5")
        .build()

    private fun parseVehicles(root: JSONObject): List<VehicleInfo> {
        val arr = root.optJSONArray("enrolledVehicleDetails") ?: return emptyList()
        val out = ArrayList<VehicleInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val wrap = arr.optJSONObject(i) ?: continue
            val vd = wrap.optJSONObject("vehicleDetails") ?: continue
            val vin = vd.optString("vin", "")
            if (vin.isBlank()) continue
            val ev = vd.optString("evStatus", "N")
            val engine = if (ev == "E") VehicleInfo.EngineType.EV else VehicleInfo.EngineType.ICE
            val regId = vd.optString("regid", "").ifBlank { vd.optString("regId", "") }
            val gen = vd.optString("vehicleGeneration", "").ifBlank { vd.optString("generation", "1") }
            out.add(
                VehicleInfo(
                    vin = vin,
                    nickname = vd.optString("nickName", vin),
                    regId = regId,
                    generation = gen.ifBlank { "1" },
                    brandIndicator = vd.optString("brandIndicator", "H"),
                    engineType = engine,
                ),
            )
        }
        return out
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
