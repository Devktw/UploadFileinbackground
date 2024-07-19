package com.example.uploadbackground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue


class FileMonitorService : Service() {

    // พาธของไดเรกทอรีที่ต้องการเฝ้าสังเกตเพื่อดูไฟล์ใหม่ และถ้ามีไฟล์เพิ่มเข้ามาที่โฟลเดอร์ของพาธนี้จะถูกอัพโหลดทันที
    private val DIRECTORY_TO_WATCH = "/storage/emulated/0/?"
    private lateinit var watchService: WatchService
    private lateinit var watchKey: WatchKey

    // ID ของช่องทางการแจ้งเตือน
    private val CHANNEL_ID = "FileMonitorServiceChannel"
    private val NOTIFICATION_ID = 12345

    private lateinit var wakeLock: PowerManager.WakeLock
    private val uploadQueue = ConcurrentLinkedQueue<File>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var sharedPreferences: SharedPreferences
    private val UPLOADED_FILES_KEY = "uploaded_files"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // รับบริการ PowerManager และสร้าง WakeLock เพื่อให้แอพทำงานต่อไปแม้ในสภาพที่ระบบประหยัดพลังงาน
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UploadBackground::FileMonitorWakeLock")
        wakeLock.acquire(Long.MAX_VALUE)

        sharedPreferences = getSharedPreferences("FileUploadPrefs", Context.MODE_PRIVATE)

        val directory = Paths.get(DIRECTORY_TO_WATCH)
        watchService = FileSystems.getDefault().newWatchService()
        watchKey = directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)

        // โหลดไฟล์ที่อัปโหลดแล้วก่อนหน้านี้
        val uploadedFiles = getUploadedFiles()

        // ตรวจสอบไฟล์ที่อยู่ในคิวแต่ยังไม่ได้อัปโหลด
        val directoryFile = File(DIRECTORY_TO_WATCH)
        directoryFile.listFiles()?.forEach { file ->
            if (!uploadedFiles.contains(file.name)) {
                uploadQueue.offer(file)
            }
        }

        // เริ่มเฝ้าสังเกตและจัดการไฟล์ใหม่ในเธรดแยกต่างหาก
        Thread {
            try {
                while (true) {
                    val key = watchService.take() ?: continue
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            val fileName = event.context() as Path
                            val newFile = File(DIRECTORY_TO_WATCH, fileName.toString())
                            // เพิ่มไฟล์ใหม่เข้าไปในคิวสำหรับอัปโหลด
                            uploadQueue.offer(newFile)
                            // เริ่มกระบวนการอัปโหลด
                            processUploadQueue()
                        }
                    }
                    key.reset()
                }
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }.start()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Monitor Service")
            .setContentText("Monitoring directory for new files")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // สร้างช่องทางการแจ้งเตือนสำหรับ Android O และเวอร์ชันที่สูงกว่า
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "File Monitor Service Channel"
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy() //เมื่อแอพเด้งให้หยุดกระบวนการทันที
        watchKey.cancel()
        watchService.close()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // ประมวลผลคิวการอัปโหลดในพื้นหลัง และจะลองอัพโหลดเรื่อยๆ เพื่อรอเชื่อมอินเทอร์เน็ต
    @RequiresApi(Build.VERSION_CODES.M)
    private fun processUploadQueue() {
        coroutineScope.launch {
            while (uploadQueue.isNotEmpty()) {
                if (isNetworkAvailable()) {
                    val file = uploadQueue.poll() ?: continue
                    if (!isFileUploaded(file.name)) {
                        val success = uploadFile(file)
                        if (success) {
                            markFileAsUploaded(file.name)
                        } else {
                            // ถ้าอัปโหลดล้มเหลว ให้นำไฟล์กลับไปใส่ในคิว
                            uploadQueue.offer(file)
                            delay(60000) // รอ 1 นาที ก่อนพยายามอัปโหลดอีกครั้ง
                        }
                    }
                } else {
                    delay(60000) // รอ 1 นาที ก่อนตรวจสอบการเชื่อมต่อเครือข่ายอีกครั้ง
                }
            }
            cleanUploadedFilesList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M) //ฟังก์ชันสำหรับเช็คการเชื่อมต่ออินเทอร์เน็ต
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    // ฟังก์ชันสำหรับอัปโหลดไฟล์
    private suspend fun uploadFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("http://10.0.0.0:800/") //ftp หรือ ที่จะเก็บไฟล์ที่อัพโหลด
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val apiService = retrofit.create(ApiService::class.java)

            val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = apiService.uploadFile(body).execute()
            if (response.isSuccessful) {
                Log.w("Upload", "Success: ${response.body()?.message}")
                true
            } else {
                Log.w("Upload", "Fail: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.w("Upload", "Error: ${e.message}")
            false
        }
    }

    // ตรวจสอบว่าไฟล์ได้รับการอัปโหลดแล้วหรือไม่
    private fun isFileUploaded(fileName: String): Boolean {
        return getUploadedFiles().contains(fileName)
    }

    // ทำเครื่องหมายว่าไฟล์ได้รับการอัปโหลดแล้ว
    private fun markFileAsUploaded(fileName: String) {
        val uploadedFiles = getUploadedFiles().toMutableSet()
        uploadedFiles.add(fileName)
        saveUploadedFiles(uploadedFiles)
    }

    // รับรายชื่อไฟล์ที่ได้รับการอัปโหลดแล้วจาก SharedPreferences
    private fun getUploadedFiles(): Set<String> {
        val json = sharedPreferences.getString(UPLOADED_FILES_KEY, null)
        return if (json != null) {
            Gson().fromJson(json, object : TypeToken<Set<String>>() {}.type)
        } else {
            emptySet()
        }
    }

    // บันทึกรายชื่อไฟล์ที่ได้รับการอัปโหลดแล้วไปยัง SharedPreferences
    private fun saveUploadedFiles(files: Set<String>) {
        val json = Gson().toJson(files)
        sharedPreferences.edit().putString(UPLOADED_FILES_KEY, json).apply()
    }

    // ล้างรายชื่อไฟล์ที่ได้รับการอัปโหลดแล้วซึ่งไม่มีในไดเรกทอรีอีกต่อไป
    private fun cleanUploadedFilesList() {
        val uploadedFiles = getUploadedFiles().toMutableSet()
        val directory = File(DIRECTORY_TO_WATCH)
        val currentFiles = directory.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        // ลบไฟล์ที่ไม่อยู่ในไดเรกทอรีอีกต่อไป
        uploadedFiles.removeAll { !currentFiles.contains(it) }

        saveUploadedFiles(uploadedFiles)
    }
}
