package com.example.sinankiosk

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandleAction(intent.action)) {
            return
        }

        KioskDevicePolicyController(context).applyDedicatedDevicePolicies()

        val launchIntent = Intent.makeRestartActivityTask(
            ComponentName(context, MainActivity::class.java)
        ).apply {
            putExtra(EXTRA_STARTED_FROM_SYSTEM_EVENT, true)
        }

        runCatching {
            context.startActivity(launchIntent)
        }.onFailure { throwable ->
            Log.w(
                TAG,
                "Failed to relaunch kiosk after system event ${intent.action}",
                throwable
            )
        }
    }

    companion object {
        internal const val EXTRA_STARTED_FROM_SYSTEM_EVENT = "started_from_system_event"

        private const val TAG = "BootReceiver"

        private val startupActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        internal fun shouldHandleAction(action: String?): Boolean = action in startupActions
    }
}
