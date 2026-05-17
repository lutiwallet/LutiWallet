package com.app.lutiwallet.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lutiwallet.LutiCryptoManager
import com.app.lutiwallet.SolanaUtils
import com.app.lutiwallet.utils.obtenerPrefsSeguras
import org.bitcoinj.crypto.MnemonicCode
import java.security.SecureRandom

@Composable
fun RegistroScreen(
    listaFrases: MutableList<String>,
    onWalletCreada: (fraseNueva: String, idx: Int) -> Unit,
    onWalletImportada: (idx: Int) -> Unit
) {
    val context = LocalContext.current
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }
    var modoImportar by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize(),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        if (!modoImportar) {
            Box(
                Modifier
                    .size(80.dp)
                    .background(Color.Cyan.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AccountBalanceWallet, null,
                    tint = Color.Cyan,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val entropy = ByteArray(16)
                    SecureRandom().nextBytes(entropy)
                    val nueva = MnemonicCode.INSTANCE.toMnemonic(entropy).joinToString(" ")
                    listaFrases.add(nueva)
                    val setParaGuardar = listaFrases.map { LutiCryptoManager.encrypt(it) }.toSet()
                    prefsSeguras.edit().putStringSet("lista_frases", setParaGuardar).apply()
                    val nuevoIdx = listaFrases.size - 1
                    prefsSeguras.edit().putInt("current_idx", nuevoIdx).apply()
                    val keyS = "frase_segura_${SolanaUtils.generateAddressFromMnemonic(nueva)}"
                    prefsSeguras.edit().putString(keyS, LutiCryptoManager.encrypt(nueva)).apply()
                    onWalletCreada(nueva, nuevoIdx)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) {
                Text("GENERAR BILLETERA", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = { modoImportar = true }) {
                Text("Importar con frase semilla", color = Color.Gray)
            }

        } else {
            var input by remember { mutableStateOf("") }

            Text(
                "Importar Billetera",
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("12 palabras semilla") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                shape = RoundedCornerShape(16.dp)
            )

            Button(
                onClick = {
                    if (input.trim().split(" ").size == 12) {
                        val fraseImportada = input.trim()
                        listaFrases.add(fraseImportada)
                        val setParaGuardar = listaFrases.map { LutiCryptoManager.encrypt(it) }.toSet()
                        prefsSeguras.edit().putStringSet("lista_frases", setParaGuardar).apply()
                        val nuevoIdx = listaFrases.size - 1
                        prefsSeguras.edit().putInt("current_idx", nuevoIdx).apply()
                        val keyS = "frase_segura_${SolanaUtils.generateAddressFromMnemonic(fraseImportada)}"
                        prefsSeguras.edit().putString(keyS, LutiCryptoManager.encrypt(fraseImportada)).apply()
                        onWalletImportada(nuevoIdx)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp, vertical = 20.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) {
                Text("IMPORTAR", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = { modoImportar = false }) {
                Text("Volver atrás", color = Color.Gray)
            }
        }
    }
}