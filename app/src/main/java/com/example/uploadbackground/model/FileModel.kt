package com.example.uploadbackground.model

import java.text.SimpleDateFormat
import java.util.Locale

data class FileModel(
    val fileName: String,
    val displayDate: String,
    val displayTime: String,
    val status: Boolean
)