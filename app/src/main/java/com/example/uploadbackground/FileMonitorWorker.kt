package com.example.uploadbackground

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.Worker
import androidx.work.WorkerParameters

class FileMonitorWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun doWork(): Result {
        val serviceIntent = Intent(applicationContext, FileMonitorService::class.java)
        applicationContext.startForegroundService(serviceIntent) // ใช้ startForegroundService แทน startService
        return Result.success()
    }
}