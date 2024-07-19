package com.example.uploadbackground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// BroadcastReceiver ที่ใช้สำหรับเริ่มต้นการทำงานหลังจากการบูตเครื่อง
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ตรวจสอบว่าการบูตเครื่องสำเร็จแล้ว
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // สร้าง PeriodicWorkRequest สำหรับทำงานซ้ำทุก 15 นาที
            val workRequest = PeriodicWorkRequestBuilder<MainActivity.FileMonitorWorker>(
                repeatInterval = 15, // ระยะเวลาที่เป็นนาที
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .build()

            // เรียกใช้ WorkManager เพื่อเริ่มต้นงานที่กำหนด
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
