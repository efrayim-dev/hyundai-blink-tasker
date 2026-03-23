package com.hyundaicompanion.bluelinktasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tasker: Send Intent → Action [ACTION_REMOTE_COMMAND] → Package + this class → Broadcast Receiver.
 * Extras: [EXTRA_COMMAND] = unlock | lock | start | stop
 *         [EXTRA_SECRET] = Tasker secret from the app
 * For `start`, optional climate extras — see [RemoteStartOptions] companion constants.
 */
class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val incoming = intent ?: return
        if (incoming.action != ACTION_REMOTE_COMMAND) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val prefs = SecurePrefs(appContext)
        val secret = incoming.getStringExtra(EXTRA_SECRET)
        val expected = prefs.taskerSecret
        if (expected.isNullOrBlank()) {
            toast(appContext, appContext.getString(R.string.tasker_secret_not_set))
            pendingResult.finish()
            return
        }
        if (secret != expected) {
            toast(appContext, appContext.getString(R.string.tasker_secret_mismatch))
            pendingResult.finish()
            return
        }
        if (!prefs.setupComplete) {
            toast(appContext, appContext.getString(R.string.setup_required))
            pendingResult.finish()
            return
        }

        val cmd = incoming.getStringExtra(EXTRA_COMMAND)?.lowercase().orEmpty()
        val repo = BlueLinkRepository.create(appContext)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = when (cmd) {
                    "unlock" -> repo.unlock()
                    "lock" -> repo.lock()
                    "start" -> {
                        val opts = RemoteStartOptions.mergeFromIntent(incoming, prefs)
                        repo.remoteStart(opts)
                    }
                    "stop" -> repo.remoteStop()
                    else -> {
                        toast(appContext, appContext.getString(R.string.tasker_unknown_command, cmd))
                        return@launch
                    }
                }
                result.fold(
                    onSuccess = { toast(appContext, it) },
                    onFailure = { toast(appContext, it.message ?: it.toString()) },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_REMOTE_COMMAND = "com.hyundaicompanion.bluelinktasker.REMOTE_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_SECRET = "secret"
    }
}
