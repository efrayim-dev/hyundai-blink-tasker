package com.hyundaicompanion.bluelinktasker

import android.content.Intent
import org.json.JSONObject

/**
 * Hyundai US remote-start climate payload (aligned with bluelinky US ICE/EV start).
 *
 * [heatedFeatures]: 0=Off, 1=Steering wheel + rear defog, 2=Rear window only, 3=Steering wheel only.
 * Seat keys: driverSeat, passengerSeat, rearLeftSeat, rearRightSeat → API field names internally.
 * Seat status: 0 Off … 8 High heat (see bluelinky seatStatusMap).
 */
data class RemoteStartOptions(
    val durationMinutes: Int = 10,
    val temperatureF: Int = 70,
    val climateOn: Boolean = false,
    val defrost: Boolean = false,
    /** 0–3 for Hyundai US */
    val heatedFeatures: Int = 0,
    val seatDriver: Int? = null,
    val seatPassenger: Int? = null,
    val seatRearLeft: Int? = null,
    val seatRearRight: Int? = null,
) {
    init {
        require(durationMinutes in 1..30) { "duration must be 1–30" }
        require(temperatureF in 60..90) { "temperature must be 60–90 °F" }
        require(heatedFeatures in 0..3) { "heatedFeatures must be 0–3" }
    }

    fun validatedSeatClimate(): Map<String, Int> {
        val map = LinkedHashMap<String, Int>()
        seatDriver?.takeIf { it in VALID_SEAT_STATUS }?.let { map[API_DRIVER] = it }
        seatPassenger?.takeIf { it in VALID_SEAT_STATUS }?.let { map[API_PASSENGER] = it }
        seatRearLeft?.takeIf { it in VALID_SEAT_STATUS }?.let { map[API_REAR_LEFT] = it }
        seatRearRight?.takeIf { it in VALID_SEAT_STATUS }?.let { map[API_REAR_RIGHT] = it }
        return map
    }

    companion object {
        val VALID_SEAT_STATUS = 0..8

        private const val API_DRIVER = "drvSeatHeatState"
        private const val API_PASSENGER = "astSeatHeatState"
        private const val API_REAR_LEFT = "rlSeatHeatState"
        private const val API_REAR_RIGHT = "rrSeatHeatState"

        fun fromSecurePrefs(prefs: SecurePrefs): RemoteStartOptions =
            RemoteStartOptions(
                durationMinutes = prefs.rsDurationMinutes.coerceIn(1, 30),
                temperatureF = prefs.rsTempF.coerceIn(60, 90),
                climateOn = prefs.rsClimateOn,
                defrost = prefs.rsDefrost,
                heatedFeatures = prefs.rsHeatedFeatures.coerceIn(0, 3),
                seatDriver = prefs.rsSeatDriver.takeIf { it in VALID_SEAT_STATUS },
                seatPassenger = prefs.rsSeatPassenger.takeIf { it in VALID_SEAT_STATUS },
                seatRearLeft = prefs.rsSeatRearLeft.takeIf { it in VALID_SEAT_STATUS },
                seatRearRight = prefs.rsSeatRearRight.takeIf { it in VALID_SEAT_STATUS },
            )

        /**
         * Tasker / intent extras override prefs when present.
         * Booleans: "true"/"1" / "false"/"0".
         * Optional seats: 0–8, or omit extra.
         */
        fun mergeFromIntent(intent: Intent, prefs: SecurePrefs): RemoteStartOptions {
            var o = fromSecurePrefs(prefs)
            if (intent.hasExtra(EXTRA_START_DURATION)) {
                o = o.copy(durationMinutes = intent.getIntExtra(EXTRA_START_DURATION, o.durationMinutes).coerceIn(1, 30))
            }
            if (intent.hasExtra(EXTRA_START_TEMP)) {
                o = o.copy(temperatureF = intent.getIntExtra(EXTRA_START_TEMP, o.temperatureF).coerceIn(60, 90))
            }
            if (intent.hasExtra(EXTRA_START_CLIMATE)) {
                o = o.copy(climateOn = readBoolExtra(intent, EXTRA_START_CLIMATE, o.climateOn))
            }
            if (intent.hasExtra(EXTRA_START_DEFROST)) {
                o = o.copy(defrost = readBoolExtra(intent, EXTRA_START_DEFROST, o.defrost))
            }
            if (intent.hasExtra(EXTRA_START_HEATED)) {
                o = o.copy(heatedFeatures = intent.getIntExtra(EXTRA_START_HEATED, o.heatedFeatures).coerceIn(0, 3))
            }
            if (intent.hasExtra(EXTRA_START_SEAT_JSON)) {
                o = o.mergeSeatJson(intent.getStringExtra(EXTRA_START_SEAT_JSON))
            } else {
                if (intent.hasExtra(EXTRA_SEAT_DRIVER)) {
                    o = o.copy(seatDriver = intent.getIntExtra(EXTRA_SEAT_DRIVER, -1).takeIf { it in VALID_SEAT_STATUS })
                }
                if (intent.hasExtra(EXTRA_SEAT_PASSENGER)) {
                    o = o.copy(seatPassenger = intent.getIntExtra(EXTRA_SEAT_PASSENGER, -1).takeIf { it in VALID_SEAT_STATUS })
                }
                if (intent.hasExtra(EXTRA_SEAT_REAR_LEFT)) {
                    o = o.copy(seatRearLeft = intent.getIntExtra(EXTRA_SEAT_REAR_LEFT, -1).takeIf { it in VALID_SEAT_STATUS })
                }
                if (intent.hasExtra(EXTRA_SEAT_REAR_RIGHT)) {
                    o = o.copy(seatRearRight = intent.getIntExtra(EXTRA_SEAT_REAR_RIGHT, -1).takeIf { it in VALID_SEAT_STATUS })
                }
            }
            return o
        }

        private fun readBoolExtra(intent: Intent, key: String, fallback: Boolean): Boolean {
            if (!intent.hasExtra(key)) return fallback
            return when (val raw = intent.extras?.get(key)) {
                is Boolean -> raw
                is Int -> raw != 0
                is String -> {
                    val s = raw.lowercase().trim()
                    when (s) {
                        "1", "true", "yes", "on" -> true
                        "0", "false", "no", "off" -> false
                        else -> fallback
                    }
                }
                else -> fallback
            }
        }

        private fun RemoteStartOptions.mergeSeatJson(json: String?): RemoteStartOptions {
            if (json.isNullOrBlank()) return this
            return try {
                val jo = JSONObject(json.trim())
                var o = this
                jo.optIntOrNull("driverSeat")?.let { if (it in VALID_SEAT_STATUS) o = o.copy(seatDriver = it) }
                jo.optIntOrNull("passengerSeat")?.let { if (it in VALID_SEAT_STATUS) o = o.copy(seatPassenger = it) }
                jo.optIntOrNull("rearLeftSeat")?.let { if (it in VALID_SEAT_STATUS) o = o.copy(seatRearLeft = it) }
                jo.optIntOrNull("rearRightSeat")?.let { if (it in VALID_SEAT_STATUS) o = o.copy(seatRearRight = it) }
                o
            } catch (_: Exception) {
                this
            }
        }

        private fun JSONObject.optIntOrNull(key: String): Int? {
            if (!has(key)) return null
            return optInt(key)
        }

        const val EXTRA_START_DURATION = "start_duration"
        const val EXTRA_START_TEMP = "start_temp"
        const val EXTRA_START_CLIMATE = "start_climate"
        const val EXTRA_START_DEFROST = "start_defrost"
        const val EXTRA_START_HEATED = "start_heated"
        const val EXTRA_START_SEAT_JSON = "start_seat_json"
        const val EXTRA_SEAT_DRIVER = "seat_driver"
        const val EXTRA_SEAT_PASSENGER = "seat_passenger"
        const val EXTRA_SEAT_REAR_LEFT = "seat_rear_left"
        const val EXTRA_SEAT_REAR_RIGHT = "seat_rear_right"
    }
}
