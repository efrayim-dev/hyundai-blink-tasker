package com.hyundaicompanion.bluelinktasker

data class VehicleInfo(
    val vin: String,
    val nickname: String,
    val regId: String,
    val generation: String,
    val brandIndicator: String,
    val engineType: EngineType,
) {
    enum class EngineType { ICE, EV }
}
