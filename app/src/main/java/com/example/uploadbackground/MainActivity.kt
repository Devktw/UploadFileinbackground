package com.example.uploadbackground

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.uploadbackground.adapter.FileAdapter
import com.example.uploadbackground.model.FileModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {


    private val PERMISSION_REQUEST_CODE = 101
    private val folderPath = "${Environment.getExternalStorageDirectory()}/test" //ตำแหนางโฟลเดอร์ที่จะตรวจสอบไฟล์ใหม่
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var sharedPreferences: SharedPreferences
    private val UPLOADED_FILES_KEY = "uploaded_files"
    private lateinit var manageExternalStorageLauncher: ActivityResultLauncher<Intent>
    private lateinit var recy_main : RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private var fileObserver: FileObserver? = null
    private val _fileStateFlow = MutableStateFlow<List<FileModel>>(emptyList())
    private val fileStateFlow: StateFlow<List<FileModel>> get() = _fileStateFlow
    private lateinit var tv : TextView
    private lateinit var progress : ProgressBar
    private lateinit var btn_upload : CardView
    private lateinit var btn_gosetting : CardView
    private lateinit var layout_permission : LinearLayout
    private lateinit var files: MutableList<FileModel>
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("FileUploadPrefs", Context.MODE_PRIVATE)

        tv = findViewById(R.id.tv)
        progress = findViewById(R.id.progress)
        btn_gosetting = findViewById(R.id.btn_gosetting)
        layout_permission = findViewById(R.id.layout_checkpermission)

        btn_gosetting.setOnClickListener(){
            checkAndRequestPermissions()

        }
        recy_main = findViewById(R.id.recy_main)
        recy_main.layoutManager = LinearLayoutManager(this)

        Log.e("asda",Environment.getExternalStorageDirectory().toString())
        btn_upload = findViewById(R.id.btn_upload)
        btn_upload.setOnClickListener {
            startUploadFile()

        }

        manageExternalStorageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    initializeFileAndStartUpload()
                    setUpFile()
                    layout_permission.visibility = View.GONE
                } else {
                    layout_permission.visibility = View.VISIBLE

                }
            }
        }


        checkAndRequestPermissions()
    }

    // ฟังก์ชันเริ่มต้น FileMonitorService และเริ่มต้นการอัพโหลด
    private fun initializeFileAndStartUpload() {

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // สร้างการร้องขอการทำงานแบบเป็นระยะสำหรับ FileMonitorWorker ที่ทำงานทุก 15 นาที
        val periodicWorkRequest = PeriodicWorkRequestBuilder<FileMonitorWorker>(
            repeatInterval = 15, // ระยะเวลาการทำซ้ำ
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()

        // เริ่มต้น WorkManager และตั้งค่าการทำงานแบบเป็นระยะ
        val workManager = WorkManager.getInstance(applicationContext)
        workManager.enqueueUniquePeriodicWork(
            "FileMonitorWorker",
            ExistingPeriodicWorkPolicy.REPLACE, // แทนที่งานที่มีชื่อเดียวกัน
            periodicWorkRequest
        )

        val serviceIntent = Intent(this, FileMonitorService::class.java)
        startService(serviceIntent)
        val boostserviceIntent = Intent(this, BootReceiver::class.java)
        startService(boostserviceIntent)
        setUpFile()

    }


    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                initializeFileAndStartUpload()
                layout_permission.visibility = View.GONE
            } else {
                layout_permission.visibility = View.VISIBLE
                requestManageAllFilesAccess()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                initializeFileAndStartUpload()
                layout_permission.visibility = View.GONE
            } else {
                layout_permission.visibility = View.VISIBLE
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                initializeFileAndStartUpload()
                layout_permission.visibility = View.GONE
            } else {
                layout_permission.visibility = View.VISIBLE
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestManageAllFilesAccess() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
            manageExternalStorageLauncher.launch(intent)
        } catch (e: Exception) {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            manageExternalStorageLauncher.launch(intent)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                layout_permission.visibility = View.GONE
                initializeFileAndStartUpload()
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

    private fun setUpFile() {
        files = mutableListOf()
        fileAdapter = FileAdapter(files, this, sharedPreferences)
        recy_main.adapter = fileAdapter

        // Load initial files immediately
        lifecycleScope.launch {
            try {
                val updatedFiles = getFilesFromFolder()
                _fileStateFlow.value = updatedFiles
                fileAdapter.updateFiles(updatedFiles)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            observeFiles()
            // Start collecting fileStateFlow changes
            fileStateFlow.collect { updatedFiles ->
                files = updatedFiles.toMutableList()
                fileAdapter.updateFiles(files)
            }
        }
    }

    private fun observeFiles() {
        fileObserver = object : FileObserver(folderPath, FileObserver.CREATE or FileObserver.DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (event == FileObserver.CREATE || event == FileObserver.DELETE) {
                    lifecycleScope.launch {
                        try {
                            val updatedFiles = getFilesFromFolder()
                            _fileStateFlow.value = updatedFiles
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }


    private suspend fun getFilesFromFolder(): List<FileModel> = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        val uploadedFiles = getUploadedFiles()

        if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.map { file ->
                val isUploaded = uploadedFiles.contains(file.name)
                FileModel(file.name, "", "", isUploaded)
            }?.sortedByDescending { it.fileName } ?: emptyList()
        } else {
            emptyList()
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun startUploadFile() {
        val directory = File(folderPath)
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e("FileMonitorService", "Failed to create directory: $folderPath")
                return
            }
        }

        val uploadedFiles = getUploadedFiles()
            directory.listFiles()?.forEach { file ->
                if (!uploadedFiles.contains(file.name)) {
                    lifecycleScope.launch {
                        processFileUpload(file)
                    }
                }
            }

    }

    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun processFileUpload(file: File) {
            if (isNetworkAvailable()) {
                if (!isFileUploaded(file.name) && file.exists()) {
                    val success = uploadFile(file)
                    withContext(Dispatchers.Main) {
                        Log.d("UploadStatus", "File: ${file.name}, Success: $success")
                        if (success) {
                            markFileAsUploaded(file.name)
                            updateFileStatus(file.name, true) // Update status on success
                        } else {
                            // Handle the failure case as needed
                            Log.e("UploadStatus", "Failed to upload file: ${file.name}")
                        }
                    }
                }
            } else {
                Log.e("NetworkError", "Network unavailable")
            }
    }

     fun updateFileStatus(fileName: String, status: Boolean) {
        coroutineScope.launch(Dispatchers.Main) {
            Log.d("UpdateFileStatus", "Current files: $files") // Log current files before updating

            // Update file status in the UI
            val updatedFiles = files.map { file ->
                if (file.fileName == fileName) {
                    file.copy(status = status)
                } else {
                    file
                }
            }.toMutableList()

            Log.d("UpdateFileStatus", "Updated files: $updatedFiles") // Log updated files

            fileAdapter.updateFiles(updatedFiles) // Notify RecyclerView adapter of changes

            // Update status in SharedPreferences
            val uploadedFiles = getUploadedFiles().toMutableSet()
            if (status) {
                uploadedFiles.add(fileName)
            } else {
                uploadedFiles.remove(fileName)
            }
            Log.d("UpdateFileStatus", "Uploaded files: $uploadedFiles") // Log uploaded files

            val editor = sharedPreferences.edit()
            editor.putString("uploaded_files", Gson().toJson(uploadedFiles))
            editor.apply()

            Log.d("UpdateFileStatus", "SharedPreferences updated") // Log update confirmation
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private suspend fun uploadFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                progress.visibility = View.VISIBLE
                tv.visibility = View.GONE
            }
            val retrofit = Retrofit.Builder()
                .baseUrl("http://0.0.0.0:8089/") //อัพโหลดไฟล์ขึ้นserver
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
        } finally {
            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
                tv.visibility = View.VISIBLE
            }
        }
    }

    // Methods for managing uploaded files
    private fun isFileUploaded(fileName: String): Boolean {
        return getUploadedFiles().contains(fileName)
    }

    private fun markFileAsUploaded(fileName: String) {
        val uploadedFiles = getUploadedFiles().toMutableSet()
        uploadedFiles.add(fileName)
        saveUploadedFiles(uploadedFiles)
    }

    private fun getUploadedFiles(): Set<String> {
        val json = sharedPreferences.getString(UPLOADED_FILES_KEY, null)
        return json?.let {
            Gson().fromJson(it, object : TypeToken<Set<String>>() {}.type)
        } ?: emptySet()
    }

    private fun saveUploadedFiles(files: Set<String>) {
        val json = Gson().toJson(files)
        sharedPreferences.edit().putString(UPLOADED_FILES_KEY, json).apply()
    }

}
