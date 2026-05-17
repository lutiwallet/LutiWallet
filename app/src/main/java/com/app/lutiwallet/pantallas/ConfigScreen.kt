package com.app.lutiwallet.pantallas

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lutiwallet.LutiCryptoManager
import com.app.lutiwallet.utils.TEXTOS_IDIOMAS
import com.app.lutiwallet.utils.hashPassword
import com.app.lutiwallet.utils.obtenerPrefsSeguras

@Composable
fun ConfigScreen(
    direccionActiva: String,
    idiomaSeleccionado: String,
    onIdiomaChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }
    val passGuardada = remember { prefsSeguras.getString("app_password", "") ?: "" }

    var passConfirmacion by remember { mutableStateOf("") }
    var mostrarFraseSegura by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(); passConfirmacion = "" },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                TEXTOS_IDIOMAS[idiomaSeleccionado]?.get("titulo") ?: "CONFIGURACIÓN",
                color = Color.White, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {


                Column {
                    Text(TEXTOS_IDIOMAS[idiomaSeleccionado]?.get("idioma") ?: "Idioma", color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    var exp by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { exp = true },
                            color = Color.White.copy(0.05f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(idiomaSeleccionado, color = Color.White)
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color.Cyan)
                            }
                        }
                        DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                            TEXTOS_IDIOMAS.keys.forEach { lang ->
                                DropdownMenuItem(text = { Text(lang) }, onClick = {
                                    onIdiomaChange(lang)
                                    prefsSeguras.edit().putString("idioma_pref", lang).apply()
                                    exp = false
                                })
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))


                Column {
                    Text(
                        TEXTOS_IDIOMAS[idiomaSeleccionado]?.get("frase") ?: "Ver Frase Semilla",
                        color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passConfirmacion,
                        onValueChange = { passConfirmacion = it },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        label = { Text("Contraseña", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (hashPassword(passConfirmacion) == passGuardada) {
                                mostrarFraseSegura = true
                            } else {
                                Toast.makeText(context, "Contraseña Incorrecta", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(TEXTOS_IDIOMAS[idiomaSeleccionado]?.get("ver_frase") ?: "VER FRASE", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))


                ActionButton(
                    mod = Modifier.fillMaxWidth(),
                    txt = TEXTOS_IDIOMAS[idiomaSeleccionado]?.get("politica") ?: "Política de Privacidad",
                    ico = Icons.Default.Info,
                    col = Color.Gray.copy(alpha = 0.3f),
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://lutiwallet.com/legal.html")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error al abrir el navegador", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); passConfirmacion = "" }) {
                Text(TEXTOS_IDIOMAS[idiomaSeleccionado]?.get("cerrar") ?: "CERRAR", color = Color.Gray)
            }
        }
    )


    if (mostrarFraseSegura) {
        var ojoFrase by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { mostrarFraseSegura = false; ojoFrase = false; passConfirmacion = "" },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("TU FRASE SEMILLA", color = Color.Cyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(12.dp)).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (ojoFrase) {
                            SelectionContainer {
                                val datoGuardado = prefsSeguras.getString("frase_segura_$direccionActiva", "") ?: ""
                                val fraseReal = try { LutiCryptoManager.decrypt(datoGuardado) } catch (e: Exception) { datoGuardado }
                                Text(fraseReal, color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp)
                            }
                        } else {
                            Text("•••• •••• •••• •••• •••• ••••", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                    IconButton(onClick = { ojoFrase = !ojoFrase }, modifier = Modifier.padding(top = 10.dp)) {
                        Icon(
                            if (ojoFrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            null, tint = Color.Cyan
                        )
                    }
                    Text(
                        "IMPORTANTE: Si alguien obtiene esta frase, tendrá acceso total a tus fondos.",
                        color = Color.Red, fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { mostrarFraseSegura = false; passConfirmacion = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                ) { Text("ENTENDIDO", color = Color.Black) }
            }
        )
    }
}