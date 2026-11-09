package com.example.surveytopografi

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SurveyPoint(
    val pointName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

object ExportHelper {

    fun exportToCSV(context: Context, pointList: List<SurveyPoint>): File? {
        if (pointList.isEmpty()) {
            Toast.makeText(context, "Tidak ada data titik untuk diekspor!", Toast.LENGTH_SHORT).show()
            return null
        }

        val fileName = "Survey_Topografi_${System.currentTimeMillis()}.csv"
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        try {
            val writer = FileWriter(file)
            writer.append("Nama_Titik,Koordinat_X,Koordinat_Y,Ketinggian_Z,Waktu\n")

            for (point in pointList) {
                writer.append("${point.pointName},${point.x},${point.y},${point.z},${point.timestamp}\n")
            }

            writer.flush()
            writer.close()

            Toast.makeText(context, "CSV Berhasil Disimpan di Downloads", Toast.LENGTH_LONG).show()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal menyimpan CSV: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }
}