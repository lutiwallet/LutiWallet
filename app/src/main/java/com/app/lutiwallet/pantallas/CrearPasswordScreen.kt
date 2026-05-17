package com.app.lutiwallet.pantallas

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
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
import com.app.lutiwallet.utils.esPasswordValida
import com.app.lutiwallet.utils.hashPassword
import com.app.lutiwallet.utils.obtenerPrefsSeguras

@Composable
fun CrearPasswordScreen(
    onPasswordCreada: () -> Unit
) {
    val context = LocalContext.current
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }

    var passInput by remember { mutableStateOf("") }
    val valida = esPasswordValida(passInput)

    Column(
        Modifier.fillMaxSize().padding(30.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Security, null,
            tint = Color.Cyan,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Seguridad de la Wallet",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "8+ caracteres, Mayúscula, Número y Símbolo",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = passInput,
            onValueChange = { passInput = it },
            label = { Text("Nueva Contraseña de Acceso") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Button(
            onClick = {
                if (valida) {
                    val hashAGuardar = hashPassword(passInput)
                    prefsSeguras.edit().putString("app_password", hashAGuardar).apply()
                    Toast.makeText(context, "Clave Guardada", Toast.LENGTH_SHORT).show()
                    onPasswordCreada()
                }
            },
            enabled = valida,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (valida) Color.Cyan else Color.DarkGray
            )
        ) {
            Text("CONTINUAR", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}