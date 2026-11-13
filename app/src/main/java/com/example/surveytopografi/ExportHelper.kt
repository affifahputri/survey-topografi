package com.example.surveytopografi

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileWriter
import java.util.Locale

data class SurveyPoint(
    val pointNumber: Int,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double,
    val d1: Double,
    val d2: Double,
    val timestamp: Long = System.currentTimeMillis()
)

object ExportHelper {
    fun exportToCsv(context: Context, points: List<SurveyPoint>): String? {
        if (points.isEmpty()) return null
        val fileName = "Survey_Topografi_${System.currentTimeMillis()}.csv"
        val csvHeader = "No,Longitude (X),Latitude (Y),Elevasi_Z (mdpl),Jarak_A1 (m),Jarak_A2 (m),Timestamp\n"
        val csvBody = points.joinToString("\n") {
            String.format(
                Locale.US,
                "%d,%.6f,%.6f,%.2f,%.2f,%.2f,%d",
                it.pointNumber, it.longitude, it.latitude, it.altitude, it.d1, it.d2, it.timestamp
            )
        }
        val content = csvHeader + csvBody

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(content.toByteArray())
                    }
                }
                "Downloads/$fileName"
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, fileName)
                FileWriter(file).use { it.write(content) }
                file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}