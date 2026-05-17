package com.app.lutiwallet

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object UpdateManager {

    private const val JSON_URL = "https://lutiwallet.com/update.json"
    private val client = OkHttpClient()

    private var expectedSha256: String = ""

    fun checkForUpdates(context: Context, onUpdateAvailable: (String) -> Unit) {
        val request = Request.Builder()
            .url(JSON_URL)
            .addHeader("Cache-Control", "no-cache")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LutiUpdate", "Error al conectar con el servidor: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonString ->
                    try {
                        val json = JSONObject(jsonString)
                        val latestVersion = json.getInt("versionCode")
                        val apkUrl = json.getString("apkUrl")
                        expectedSha256 = json.optString("apkSha256", "").lowercase()

                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        val currentVersion: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        }

                        if (latestVersion.toLong() > currentVersion) {
                            Log.d("LutiUpdate", "Nueva versión disponible")
                            Handler(Looper.getMainLooper()).post {
                                onUpdateAvailable(apkUrl)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LutiUpdate", "Error al procesar la actualización: ${e.message}")
                    }
                }
            }
        })
    }

    fun downloadAndInstall(context: Context, url: String) {
        val appContext = context.applicationContext
        val destination = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "lutiwallet_update.apk")

        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Actualizando LutiWallet")
            .setDescription("Descargando la última versión...")
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, "lutiwallet_update.apk")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                val exitoso = cursor.use { c ->
                    c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }

                if (!exitoso) {
                    destination.delete()
                    try { appContext.unregisterReceiver(this) } catch (e: Exception) { }
                    return
                }

                if (expectedSha256.isNotEmpty()) {
                    val actualHash = calcularSha256(destination)
                    if (actualHash != expectedSha256) {
                        Log.e("LutiUpdate", "Verificación de integridad FALLIDA. APK descartada.")
                        destination.delete()
                        try { appContext.unregisterReceiver(this) } catch (e: Exception) { }
                        return
                    }
                }

                installApk(appContext, destination)
                try {
                    appContext.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.e("LutiUpdate", "Error desregistrando: ${e.message}")
                }
            }
        }

        ContextCompat.registerReceiver(
            appContext,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun calcularSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LutiUpdate", "Error al instalar APK: ${e.message}")
        }
    }
}
