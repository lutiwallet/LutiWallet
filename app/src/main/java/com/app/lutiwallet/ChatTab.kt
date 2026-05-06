package com.app.lutiwallet

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import androidx.annotation.Keep
import com.google.firebase.database.PropertyName

@Keep
data class Mensaje(
    val textoCifrado: String = "",
    val emisor: String = "",
    val receptor: String = "",
    val timestamp: Long = System.currentTimeMillis()
)



fun procesarCripto(texto: String, llave: String, modo: Int): String {
    return try {
        val keySpec = SecretKeySpec(llave.take(16).toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(modo, keySpec)
        if (modo == Cipher.ENCRYPT_MODE) {
            val bytes = cipher.doFinal(texto.toByteArray())
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } else {
            val decoded = Base64.decode(texto, Base64.DEFAULT)
            String(cipher.doFinal(decoded))
        }
    } catch (e: Exception) {
        if (modo == Cipher.DECRYPT_MODE) "[Mensaje Cifrado]" else "Error"
    }
}

private fun enviarNotificacionFCM(destino: String, mensaje: String, emisor: String) {
    android.util.Log.d("LutiChat", "Notificando a $destino")
}


@Composable
fun ChatTab(
    direccionPropia: String = "",
    alVolverABilletera: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("LutiWalletPrefs", Context.MODE_PRIVATE)

    var terminosAceptados by remember {
        mutableStateOf(sharedPrefs.getBoolean("terminos_chat_aceptados", false))
    }

    if (!terminosAceptados) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF18181F)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "PRIVACIDAD Y TÉRMINOS",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Para usar LutiChat, debes aceptar nuestras políticas de uso y privacidad.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://lutiwallet.com/legal.html"))
                    context.startActivity(intent)
                }) {
                    Text("Leer Términos y Condiciones", color = Color.Cyan)
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        sharedPrefs.edit().putBoolean("terminos_chat_aceptados", true).apply()
                        terminosAceptados = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                ) {
                    Text("ACEPTAR Y CONTINUAR", color = Color.Black)
                }
                TextButton(onClick = alVolverABilletera) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        }
    } else {

        PantallaPrincipalChat(direccionPropia)
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PantallaPrincipalChat(direccionPropia: String) {
    val context = LocalContext.current


    val nombreArchivo = "LutiContacts_${direccionPropia.take(10)}"
    val sharedPrefs = remember(direccionPropia) {
        context.getSharedPreferences(nombreArchivo, Context.MODE_PRIVATE)
    }


    var mostrarMenuBloqueados by remember { mutableStateOf(false) }
    var mensajeInput by remember { mutableStateOf("") }
    var destinoInput by remember { mutableStateOf("") }
    var mostrarMenuContacto by remember { mutableStateOf(false) }
    var contactoSeleccionado by remember { mutableStateOf("") }
    var mostrarDialogoAlias by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }

    val todosLosMensajes = remember { mutableStateListOf<Mensaje>() }
    val contactosMap = remember { mutableStateMapOf<String, String>() }
    val solicitudesPendientes = remember { mutableStateListOf<String>() }
    val mensajesSinLeer = remember { mutableStateListOf<String>() }

    val db = FirebaseDatabase
        .getInstance("https://lutiwallet-default-rtdb.firebaseio.com/")
        .getReference("chats")


    LaunchedEffect(direccionPropia) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                FirebaseDatabase.getInstance().getReference("tokens")
                    .child(direccionPropia)
                    .setValue(token)
            }
        }
    }

    LaunchedEffect(direccionPropia) {
        contactosMap.clear()
        sharedPrefs.all.filterKeys { it.startsWith("alias_") }.forEach { (k, v) ->
            contactosMap[k.removePrefix("alias_")] = v.toString()
        }
    }


    LaunchedEffect(direccionPropia) {
        db.child("privados").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val m = child.getValue(Mensaje::class.java)
                    if (m != null && m.receptor == direccionPropia) {
                        val estaBloqueado = sharedPrefs.getBoolean("rechazado_${m.emisor}", false)
                        val ultimoVisto = sharedPrefs.getLong("ultimo_visto_${m.emisor}", 0L)
                        val esMensajeNuevo = m.timestamp > ultimoVisto

                        if (!estaBloqueado && !contactosMap.containsKey(m.emisor) && !solicitudesPendientes.contains(m.emisor)) {
                            solicitudesPendientes.add(m.emisor)
                        }

                        if (contactosMap.containsKey(m.emisor) &&
                            destinoInput != m.emisor &&
                            esMensajeNuevo &&
                            !mensajesSinLeer.contains(m.emisor)) {
                            mensajesSinLeer.add(m.emisor)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    LaunchedEffect(destinoInput, direccionPropia) {
        if (destinoInput.isNotEmpty()) {
            mensajesSinLeer.remove(destinoInput)
            sharedPrefs.edit().putLong("ultimo_visto_$destinoInput", System.currentTimeMillis()).apply()

            val path = if (destinoInput.startsWith("GLOBAL_")) destinoInput else "privados"
            db.child(path).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    todosLosMensajes.clear()
                    snapshot.children.forEach { child ->
                        val m = child.getValue(Mensaje::class.java)
                        if (m != null) {
                            if (path.startsWith("GLOBAL_") || m.receptor == direccionPropia || m.emisor == direccionPropia) {
                                todosLosMensajes.add(m)
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "LUTICHAT",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF18181F)
                ),
                actions = {
                    IconButton(onClick = { mostrarMenuBloqueados = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = Color.Cyan
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF18181F)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF18181F))
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "CUENTA ACTIVA: ${direccionPropia.take(6)}...",
                fontSize = 9.sp,
                color = Color.Cyan.copy(0.7f)
            )

            Spacer(Modifier.height(16.dp))


            if (solicitudesPendientes.isNotEmpty()) {
                Text("SOLICITUDES DE CHAT", fontSize = 11.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)
                solicitudesPendientes.forEach { remitente ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, Color.Cyan.copy(0.3f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(remitente.take(12) + "...", color = Color.White, modifier = Modifier.weight(1f))


                            IconButton(onClick = { solicitudesPendientes.remove(remitente) }) {
                                Icon(Icons.Default.Close, null, tint = Color.Red.copy(0.7f))
                            }

                            IconButton(onClick = {
                                solicitudesPendientes.remove(remitente)
                                sharedPrefs.edit().putBoolean("rechazado_$remitente", true).apply()
                            }) {
                                Icon(Icons.Rounded.Block, null, tint = Color.Gray)
                            }

                            IconButton(onClick = {
                                solicitudesPendientes.remove(remitente)
                                val alias = remitente.take(6)
                                contactosMap[remitente] = alias
                                sharedPrefs.edit().putString("alias_$remitente", alias).apply()
                            }) {
                                Icon(Icons.Default.Check, null, tint = Color.Cyan)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
            }


            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GLOBAL_ES" to "Español", "GLOBAL_EN" to "English").forEach { (id, label) ->
                    val sel = destinoInput == id
                    Button(
                        onClick = { destinoInput = if (sel) "" else id },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if(sel) Color.Cyan else Color.DarkGray),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(label, color = if(sel) Color.Black else Color.White) }
                }
            }


            if (contactosMap.isNotEmpty()) {
                LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(contactosMap.keys.toList()) { addr ->
                        val sel = destinoInput == addr
                        val tieneNuevo = mensajesSinLeer.contains(addr)
                        Box {
                            Surface(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        destinoInput = if (sel) "" else addr
                                        mensajesSinLeer.remove(addr)
                                    },
                                    onLongClick = {
                                        contactoSeleccionado = addr
                                        mostrarMenuContacto = true
                                    }
                                ),
                                color = if(sel) Color.Cyan else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if(tieneNuevo) Color.Cyan else Color.White.copy(0.2f))
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = if(sel) Color.Black else Color.Cyan)
                                    Spacer(Modifier.width(6.dp))
                                    Text(contactosMap[addr] ?: addr.take(6), color = if(sel) Color.Black else Color.White)
                                }
                            }
                            if (tieneNuevo && !sel) {
                                Box(Modifier.size(10.dp).background(Color.Cyan, CircleShape).align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
                            }
                        }
                    }
                }
            }


            OutlinedTextField(
                value = if(destinoInput.startsWith("GLOBAL_")) "" else destinoInput,
                onValueChange = { destinoInput = it },
                label = { Text("Enviar a (Dirección Solana)", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                trailingIcon = {
                    val yaTieneAlias = contactosMap.containsKey(destinoInput)
                    if (destinoInput.length > 10 && !destinoInput.startsWith("GLOBAL_") && !yaTieneAlias) {
                        IconButton(onClick = { contactoSeleccionado = destinoInput; mostrarDialogoAlias = true }) {
                            Icon(Icons.Default.Check, null, tint = Color.Cyan)
                        }
                    } else if (yaTieneAlias) {
                        Icon(Icons.Default.Person, null, tint = Color.Cyan.copy(0.5f), modifier = Modifier.size(20.dp))
                    }
                }
            )

            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (destinoInput.isNotBlank()) {
                    val chatFiltrado = todosLosMensajes.sortedBy { it.timestamp }.filter {
                        if(destinoInput.startsWith("GLOBAL_")) it.receptor == destinoInput
                        else (it.receptor == destinoInput && it.emisor == direccionPropia) ||
                                (it.receptor == direccionPropia && it.emisor == destinoInput)
                    }
                    items(chatFiltrado) { msg ->
                        val esMio = msg.emisor == direccionPropia
                        val textoFinal = when {
                            msg.receptor.startsWith("GLOBAL_") -> msg.textoCifrado
                            esMio -> procesarCripto(msg.textoCifrado, msg.receptor, Cipher.DECRYPT_MODE)
                            else -> procesarCripto(msg.textoCifrado, direccionPropia, Cipher.DECRYPT_MODE)
                        }
                        ChatBubble(msg, esMio, textoFinal)
                    }
                }
            }


            if (destinoInput.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = mensajeInput,
                        onValueChange = { mensajeInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje...", color = Color.Gray) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (mensajeInput.isNotBlank()) {
                                val mensajeParaNotificar = mensajeInput
                                val contenido = if(destinoInput.startsWith("GLOBAL_")) mensajeInput
                                else procesarCripto(mensajeInput, destinoInput, Cipher.ENCRYPT_MODE)

                                db.child(if (destinoInput.startsWith("GLOBAL_")) destinoInput else "privados")
                                    .push().setValue(Mensaje(contenido, direccionPropia, destinoInput))

                                if (!destinoInput.startsWith("GLOBAL_")) {
                                    enviarNotificacionFCM(destinoInput, mensajeParaNotificar, direccionPropia)
                                }
                                mensajeInput = ""
                            }
                        },
                        modifier = Modifier.size(48.dp).background(Color.Cyan, RoundedCornerShape(14.dp))
                    ) { Icon(Icons.Rounded.Send, null, tint = Color.Black) }
                }
            }
        }
    }

    if (mostrarMenuBloqueados) {
        DialogoUsuariosBloqueados(
            sharedPrefs = sharedPrefs,
            onDismiss = { mostrarMenuBloqueados = false }
        )
    }

    if (mostrarMenuContacto) {
        AlertDialog(
            onDismissRequest = { mostrarMenuContacto = false },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text("GESTIONAR CONTACTO",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Dirección: ${contactoSeleccionado.take(8)}...", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            aliasInput = contactosMap[contactoSeleccionado] ?: ""
                            mostrarDialogoAlias = true
                            mostrarMenuContacto = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan.copy(0.1f)),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Editar Apodo", color = Color.Cyan) }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            contactosMap.remove(contactoSeleccionado)
                            sharedPrefs.edit().remove("alias_$contactoSeleccionado").apply()
                            if (destinoInput == contactoSeleccionado) destinoInput = ""
                            mostrarMenuContacto = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.05f)),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Eliminar de la lista", color = Color.White) }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            sharedPrefs.edit().putBoolean("rechazado_$contactoSeleccionado", true).apply()
                            contactosMap.remove(contactoSeleccionado)
                            sharedPrefs.edit().remove("alias_$contactoSeleccionado").apply()
                            if (destinoInput == contactoSeleccionado) destinoInput = ""
                            mostrarMenuContacto = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.Block, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bloquear y Reportar", color = Color.Red)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mostrarMenuContacto = false }) { Text("CERRAR", color = Color.Gray) } }
        )
    }

    if (mostrarDialogoAlias) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoAlias = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Asignar Nombre", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    placeholder = { Text("Ej: Inversor SOL") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = contactoSeleccionado.ifEmpty { destinoInput }
                    val n = aliasInput.ifBlank { target.take(6) }
                    contactosMap[target] = n
                    sharedPrefs.edit().putString("alias_$target", n).apply()
                    mostrarDialogoAlias = false
                    contactoSeleccionado = ""
                    aliasInput = ""
                }) { Text("Guardar", color = Color.Cyan) }
            }
        )
    }
}

@Composable
fun DialogoUsuariosBloqueados(sharedPrefs: android.content.SharedPreferences, onDismiss: () -> Unit) {

    val bloqueados = sharedPrefs.all.filterKeys { it.startsWith("rechazado_") && sharedPrefs.getBoolean(it, false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Usuarios Bloqueados", color = Color.White) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                if (bloqueados.isEmpty()) {
                    item { Text("No tienes usuarios bloqueados.", color = Color.Gray) }
                } else {
                    items(bloqueados.keys.toList()) { key ->
                        val address = key.removePrefix("rechazado_")
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(address.take(12) + "...", color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                sharedPrefs.edit().remove(key).apply()
                                onDismiss()
                            }) {
                                Text("DESBLOQUEAR", color = Color.Cyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CERRAR", color = Color.Cyan) }
        }
    )
}


@Composable
fun ChatBubble(msg: Mensaje, esMio: Boolean, texto: String) {
    val alineacion = if (esMio) Alignment.CenterEnd else Alignment.CenterStart
    val colorFondo = if (esMio) Color.Cyan.copy(0.2f) else Color.White.copy(0.08f)

    Box(Modifier.fillMaxWidth(), contentAlignment = alineacion) {
        Column(horizontalAlignment = if(esMio) Alignment.End else Alignment.Start) {
            if (!esMio) Text(msg.emisor.take(6), color = Color.Cyan, fontSize = 9.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Surface(
                color = colorFondo,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if(esMio) 16.dp else 0.dp,
                    bottomEnd = if(esMio) 0.dp else 16.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(0.1f))
            ) {
                Text(texto, color = Color.White, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }
    }
}