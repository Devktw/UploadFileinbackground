package com.example.uploadbackground

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class RestartServiceReceiver : BroadcastReceiver() {

    //ตั้งเวลาต่ำกว่านี้อาจจะต้องแก้เพิ่มนะครับ
    companion object {
        // กำหนด Action string สำหรับ Intent ที่จะใช้ในการรีสตาร์ทเซอร์วิส
        private const val ACTION_RESTART_SERVICE = "com.example.uploadbackground.RESTART_SERVICE"

        // ฟังก์ชันสำหรับตั้งค่า AlarmManager เพื่อเรียกใช้ BroadcastReceiver เป็นระยะ
        fun scheduleAlarm(context: Context) {
            // สร้าง Intent สำหรับ BroadcastReceiver และกำหนด action
            val intent = Intent(context, RestartServiceReceiver::class.java).apply {
                action = ACTION_RESTART_SERVICE
            }
            // สร้าง PendingIntent ที่จะใช้ใน AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            // รับ AlarmManager จากระบบ
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // ตั้งค่า AlarmManager ให้เรียกใช้ BroadcastReceiver ทุก 15 นาที
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 15 * 60 * 1000, // ตั้งเวลาครั้งแรกเป็น 15 นาทีหลังจากนี้
                15 * 60 * 1000, // ตั้งเวลาในการทำซ้ำเป็นทุก 15 นาที
                pendingIntent
            )
        }
    }

    // เมธอดนี้จะถูกเรียกเมื่อ BroadcastReceiver ได้รับสัญญาณ
    override fun onReceive(context: Context, intent: Intent?) {
        // ตรวจสอบว่า Action ของ Intent ตรงกับที่คาดหวังหรือไม่
        if (intent?.action == ACTION_RESTART_SERVICE) {
            // สร้าง Intent สำหรับเริ่มต้น FileMonitorService
            val serviceIntent = Intent(context, FileMonitorService::class.java)
            // เริ่มต้น FileMonitorService
            context.startService(serviceIntent)
        }
    }
}
