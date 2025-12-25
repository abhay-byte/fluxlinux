package com.fluxlinux.app.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApkInstaller(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun downloadAndInstall(url: String, fileName: String, onProgress: (Float, String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                onProgress(0f, "Starting download...")
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) throw Exception("Failed to download file: $response")

                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()
                val file = File(context.externalCacheDir, fileName)
                
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(file)
                
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                                onProgress(progress, "Downloading... ${(progress * 100).toInt()}%")
                            }
                        }
                    }
                }
                
                onProgress(1f, "Installing...")
                installApk(file)
            } catch (e: Exception) {
                onProgress(0f, "Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        
        // Wrap in New Task for non-activity context usage if needed, 
        // but here we are calling from Activity mostly.
        // However, standard ACTION_INSTALL_PACKAGE handles UI.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
