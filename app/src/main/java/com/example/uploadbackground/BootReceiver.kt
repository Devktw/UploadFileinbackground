package com.example.uploadbackground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed broadcast received")
            initializeFileAndStartUpload(context)
        }
    }

    private fun initializeFileAndStartUpload(context: Context) {
        Log.d("BootReceiver", "Initializing file and starting upload")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<FileMonitorWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            "FileMonitorWorker",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
    }
}
