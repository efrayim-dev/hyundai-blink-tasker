package com.hyundaicompanion.bluelinktasker

/**
 * Hyundai US BlueLink API endpoints (same values as community bluelinky library).
 * Unofficial; may change without notice.
 */
data class BlueLinkEnvironment(
    val host: String,
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String,
) {
    companion object {
        fun hyundaiUs(): BlueLinkEnvironment {
            val host = "api.telematics.hyundaiusa.com"
            return BlueLinkEnvironment(
                host = host,
                baseUrl = "https://$host",
                clientId = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920",
                clientSecret = "v558o935-6nne-423i-baa8",
            )
        }
    }
}
