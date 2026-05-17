package com.app.lutiwallet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class LutiMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences("LutiWalletPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token_local", token)
            .apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        val direccionEmisor = remoteMessage.data["sender_address"] ?: ""
        val direccionPropia = remoteMessage.data["receiver_address"] ?: ""

        remoteMessage.notification?.let {
            mostrarNotificacion(it.title ?: "Nuevo Mensaje", it.body ?: "", direccionEmisor, direccionPropia)
        } ?: run {
            val tituloAlt = remoteMessage.data["title"] ?: "Nuevo Mensaje"
            val bodyAlt = remoteMessage.data["body"] ?: ""
            if (bodyAlt.isNotEmpty()) {
                mostrarNotificacion(tituloAlt, bodyAlt, direccionEmisor, direccionPropia)
            }
        }
    }

    private fun mostrarNotificacion(titulo: String, mensaje: String, emisor: String, propia: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "luti_chat_channel"

        val nombreAMostrar = obtenerAliasLocal(this, propia, emisor)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LutiChat: $nombreAMostrar")
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "LutiChat", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        manager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun obtenerAliasLocal(context: Context, direccionPropia: String, direccionEmisor: String): String {
        if (direccionEmisor.isEmpty()) return "Desconocido"

        val nombreArchivo = "LutiContacts_${direccionPropia.take(10)}"
        val prefs = context.getSharedPreferences(nombreArchivo, Context.MODE_PRIVATE)

        val alias = prefs.getString("alias_$direccionEmisor", null)

        return alias ?: if (direccionEmisor.length > 10) {
            "${direccionEmisor.take(4)}...${direccionEmisor.takeLast(4)}"
        } else {
            direccionEmisor
        }
    }
}