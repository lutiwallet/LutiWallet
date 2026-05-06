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

object UpdateManager {

    private const val JSON_URL = "https://lutiwallet.com/update.json"
    private val client = OkHttpClient()

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

                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

                        var currentVersion: Long = 0
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            currentVersion = packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            currentVersion = packageInfo.versionCode.toLong()
                        }


                        Log.d("LutiUpdate", "Versión en Servidor (JSON): $latestVersion")
                        Log.d("LutiUpdate", "Versión en App (Local): $currentVersion")


                        if (latestVersion.toLong() > currentVersion) {
                            Log.d("LutiUpdate", "¡Nueva versión disponible!")
                            Handler(Looper.getMainLooper()).post {
                                onUpdateAvailable(apkUrl)
                            }
                        } else {
                            Log.d("LutiUpdate", "La aplicación ya está actualizada.")
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
            .setDestinationUri(Uri.fromFile(destination))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    installApk(appContext, destination)
                    try {
                        appContext.unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.e("LutiUpdate", "Error desregistrando: ${e.message}")
                    }
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