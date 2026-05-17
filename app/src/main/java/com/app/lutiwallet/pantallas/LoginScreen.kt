package com.app.lutiwallet.pantallas

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.app.lutiwallet.utils.verificarPassword
import com.app.lutiwallet.utils.mostrarBiometria
import com.app.lutiwallet.utils.obtenerPrefsSeguras

@Composable
fun LoginScreen(
    onLoginExitoso: () -> Unit,
    textoDesbloquear: String,
    textoLogin: String
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }
    val passGuardada = remember { prefsSeguras.getString("app_password", "") ?: "" }

    var passInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (passGuardada.isNotEmpty() && activity != null) {
            mostrarBiometria(
                activity = activity,
                onSuccess = { onLoginExitoso() },
                onError = {
                    Toast.makeText(context, "Biometría no disponible", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().padding(30.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Lock, null,
            tint = Color.Cyan,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "LUTIWALLET",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            textoLogin,
            fontSize = 10.sp,
            color = Color.Cyan,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = passInput,
            onValueChange = { passInput = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Button(
            onClick = {
                val claveEnDisco = prefsSeguras.getString("app_password", "") ?: ""
                if (verificarPassword(passInput, claveEnDisco, prefsSeguras)) {
                    onLoginExitoso()
                    passInput = ""
                } else {
                    Toast.makeText(context, "Contraseña Incorrecta", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
        ) {
            Text(textoDesbloquear, color = Color.Black, fontWeight = FontWeight.Bold)
        }

        if (passGuardada.isNotEmpty() && activity != null) {
            Spacer(Modifier.height(20.dp))
            IconButton(onClick = {
                mostrarBiometria(
                    activity = activity,
                    onSuccess = { onLoginExitoso(); passInput = "" },
                    onError = {
                        Toast.makeText(context, "Biometría no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
                    }
                )
            }) {
                Icon(
                    Icons.Rounded.Fingerprint,
                    contentDescription = "Biometría",
                    tint = Color.Cyan,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}