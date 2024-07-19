package com.example.uploadbackground

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// Interface สำหรับการติดต่อกับ API ที่ใช้ในการอัพโหลดไฟล์
interface ApiService {

    // ฟังก์ชันอัพโหลดไฟล์ไปยังเซิร์ฟเวอร์
    @Multipart
    @POST("upload")
    fun uploadFile(@Part file: MultipartBody.Part): Call<UploadResponse>

    // Data class สำหรับการตอบกลับจากการอัพโหลดไฟล์
    data class UploadResponse(
        val success: Boolean, // ระบุว่าการอัพโหลดสำเร็จหรือไม่
        val message: String  // ข้อความที่ส่งกลับจากเซิร์ฟเวอร์
    )
}
