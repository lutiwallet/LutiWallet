package com.app.lutiwallet.modelo

import android.content.Context
import com.app.lutiwallet.utils.obtenerPrefsSeguras
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class Transaccion(
    val tipo: String,
    val monto: String,
    val fecha: String,
    val hora: String,
    val esEnvio: Boolean,
    val de: String,
    val para: String,
    val estado: String = "Éxito",
    val direccionOwner: String,
    val tokenSymbol: String = "SOL",
    val tokenSymbolDestino: String = "",
    val montoDestino: String = "",
    val esSwap: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

private fun dbHistorial(direccionOwner: String) =
    FirebaseDatabase
        .getInstance("https://lutiwallet-default-rtdb.firebaseio.com/")
        .getReference("historial")
        .child(direccionOwner)

fun agregarTransaccionFirebase(tx: Transaccion) {
    val map = hashMapOf<String, Any>(
        "tipo" to tx.tipo,
        "monto" to tx.monto,
        "fecha" to tx.fecha,
        "hora" to tx.hora,
        "esEnvio" to tx.esEnvio,
        "de" to tx.de,
        "para" to tx.para,
        "estado" to tx.estado,
        "direccionOwner" to tx.direccionOwner,
        "tokenSymbol" to tx.tokenSymbol,
        "tokenSymbolDestino" to tx.tokenSymbolDestino,
        "montoDestino" to tx.montoDestino,
        "esSwap" to tx.esSwap,
        "timestamp" to tx.timestamp
    )
    dbHistorial(tx.direccionOwner).push().setValue(map)
}

fun cargarHistorialFirebase(direccionOwner: String, onLoaded: (List<Transaccion>) -> Unit) {
    val corte = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000)
    val ref = dbHistorial(direccionOwner)

    // Borrar entradas > 31 días
    ref.orderByChild("timestamp").endAt(corte.toDouble())
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { s.children.forEach { it.ref.removeValue() } }
            override fun onCancelled(e: DatabaseError) {}
        })

    // Cargar entradas válidas
    ref.orderByChild("timestamp").startAt((corte + 1).toDouble())
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<Transaccion>()
                snapshot.children.forEach { child ->
                    @Suppress("UNCHECKED_CAST")
                    val m = child.value as? Map<String, Any> ?: return@forEach
                    lista.add(Transaccion(
                        tipo = m["tipo"] as? String ?: "",
                        monto = m["monto"] as? String ?: "",
                        fecha = m["fecha"] as? String ?: "",
                        hora = m["hora"] as? String ?: "",
                        esEnvio = m["esEnvio"] as? Boolean ?: false,
                        de = m["de"] as? String ?: "",
                        para = m["para"] as? String ?: "",
                        estado = m["estado"] as? String ?: "Éxito",
                        direccionOwner = m["direccionOwner"] as? String ?: direccionOwner,
                        tokenSymbol = m["tokenSymbol"] as? String ?: "SOL",
                        tokenSymbolDestino = m["tokenSymbolDestino"] as? String ?: "",
                        montoDestino = m["montoDestino"] as? String ?: "",
                        esSwap = m["esSwap"] as? Boolean ?: false,
                        timestamp = (m["timestamp"] as? Long) ?: (m["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    ))
                }
                onLoaded(lista.sortedByDescending { it.timestamp })
            }
            override fun onCancelled(e: DatabaseError) {}
        })
}

fun guardarHistorialEnDisco(context: Context, lista: List<Transaccion>) {
    val prefsSeguras = obtenerPrefsSeguras(context)
    val data = lista.joinToString(";") {
        "${it.tipo}|${it.monto}|${it.fecha}|${it.hora}|${it.esEnvio}|${it.de}|${it.para}|${it.estado}|${it.direccionOwner}|${it.tokenSymbol}|${it.tokenSymbolDestino}|${it.montoDestino}|${it.esSwap}|${it.timestamp}"
    }
    prefsSeguras.edit().putString("historial_raw", data).apply()
}

fun cargarHistorialDesdeDisco(context: Context): List<Transaccion> {
    val prefsSeguras = obtenerPrefsSeguras(context)
    val raw = prefsSeguras.getString("historial_raw", "") ?: ""
    if (raw.isEmpty()) return emptyList()
    return raw.split(";").mapNotNull {
        val parts = it.split("|")
        if (parts.size >= 9) {
            Transaccion(
                tipo = parts[0],
                monto = parts[1],
                fecha = parts[2],
                hora = parts[3],
                esEnvio = parts[4].toBoolean(),
                de = parts[5],
                para = parts[6],
                estado = parts[7],
                direccionOwner = parts[8],
                tokenSymbol = parts.getOrElse(9) { "SOL" },
                tokenSymbolDestino = parts.getOrElse(10) { "" },
                montoDestino = parts.getOrElse(11) { "" },
                esSwap = parts.getOrElse(12) { "false" }.toBoolean(),
                timestamp = parts.getOrElse(13) { "0" }.toLongOrNull() ?: System.currentTimeMillis()
            )
        } else null
    }
}
