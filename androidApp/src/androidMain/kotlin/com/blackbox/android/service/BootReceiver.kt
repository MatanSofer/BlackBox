package com.blackbox.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blackbox.domain.util.BlackBoxLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * BroadcastReceiver that restarts [BlackBoxService] after device boot.
 *
 * Listens for [Intent.ACTION_BOOT_COMPLETED] and starts the foreground
 * service to resume data collection. Requires `RECEIVE_BOOT_COMPLETED`
 * permission in the manifest.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val logger: BlackBoxLogger by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            logger.i(TAG, "Boot completed, starting BlackBoxService")
            val serviceIntent = BlackBoxService.newIntent(context)
            context.startForegroundService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
