package com.hyundaicompanion.bluelinktasker

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

object BiometricHelper {

    @Suppress("DEPRECATION")
    fun canAuthenticate(activity: AppCompatActivity): Boolean {
        val bm = BiometricManager.from(activity)
        val code = if (Build.VERSION.SDK_INT >= 30) {
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            bm.canAuthenticate()
        }
        return code == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: AppCompatActivity,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailed(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onFailed(activity.getString(R.string.biometric_not_recognized))
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_title))
            .setSubtitle(activity.getString(R.string.biometric_subtitle))
            .setNegativeButtonText(activity.getString(android.R.string.cancel))
            .build()
        prompt.authenticate(info)
    }
}
