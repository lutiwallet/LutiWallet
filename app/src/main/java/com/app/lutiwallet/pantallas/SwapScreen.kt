package com.app.lutiwallet.pantallas

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.lutiwallet.SolanaManager
import com.app.lutiwallet.modelo.Token
import com.app.lutiwallet.modelo.Transaccion
import com.app.lutiwallet.modelo.agregarTransaccionFirebase
import com.app.lutiwallet.modelo.guardarHistorialEnDisco
import com.app.lutiwallet.utils.COMISION_PORCENTAJE
import com.app.lutiwallet.utils.DIRECCION_TESORERIA
import com.app.lutiwallet.utils.JupiterManager
import com.app.lutiwallet.utils.format
import com.app.lutiwallet.viewmodel.WalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SwapScreen(
    viewModel: WalletViewModel,
    tokenDestino: Token,
    tokens: List<Token>,
    fraseActiva: String,
    direccionActiva: String,
    historialTotal: MutableList<Transaccion>,
    onDismiss: () -> Unit,
    onSwapExitoso: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tokenOrigen by remember {
        mutableStateOf(tokens.firstOrNull { it.esSOL } ?: tokens.firstOrNull() ?: tokenDestino)
    }
    var montoInput by remember { mutableStateOf("") }
    var cotizacion by remember { mutableStateOf<JSONObject?>(null) }
    var montoSalida by remember { mutableStateOf<Double?>(null) }
    var cargandoCotizacion by remember { mutableStateOf(false) }
    var ejecutandoSwap by remember { mutableStateOf(false) }
    var errorMsj by remember { mutableStateOf<String?>(null) }
    var expandirOrigen by remember { mutableStateOf(false) }
    var paso by remember { mutableStateOf(1) }

    val montoNum = montoInput.toDoubleOrNull() ?: 0.0
    val precioSol = tokens.firstOrNull { it.esSOL }?.precioUsd ?: 0.0
    val comisionLutiSol = if (tokenOrigen.esSOL) montoNum * COMISION_PORCENTAJE else {
        val valorUsdEnvio = montoNum * tokenOrigen.precioUsd
        if (precioSol > 0) valorUsdEnvio * COMISION_PORCENTAJE / precioSol else 0.0
    }
    val gasFee = 0.000005
    val totalComisionSol = comisionLutiSol + gasFee

    fun obtenerCotizacion() {
        val monto = montoInput.toDoubleOrNull() ?: return
        if (monto <= 0) return
        scope.launch {
            cargandoCotizacion = true
            errorMsj = null
            cotizacion = null
            montoSalida = null
            try {
                val lamports = (monto * Math.pow(10.0, tokenOrigen.decimals.toDouble())).toLong()
                val quote = JupiterManager.obtenerCotizacion(
                    mintOrigen = tokenOrigen.mintAddress,
                    mintDestino = tokenDestino.mintAddress,
                    monto = lamports
                )
                if (quote != null) {
                    cotizacion = quote
                    val outAmount = quote.getLong("outAmount")
                    montoSalida = outAmount.toDouble() / Math.pow(10.0, tokenDestino.decimals.toDouble())
                } else {
                    errorMsj = "No se encontró ruta para este swap"
                }
            } catch (e: Exception) {
                errorMsj = "Error al cotizar: ${e.localizedMessage}"
            } finally {
                cargandoCotizacion = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!ejecutandoSwap) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (!ejecutandoSwap) {
                            if (paso == 2) paso = 1 else onDismiss()
                        }
                    }) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (paso == 1) "SWAP" else "CONFIRMAR SWAP",
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp
                    )
                }

                if (paso == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        Column {
                            Text("DESDE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(8.dp))
                            Box {
                                Surface(
                                    onClick = { if (tokens.size > 1) expandirOrigen = true },
                                    color = Color.White.copy(0.05f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier.size(40.dp).background(Color.Cyan.copy(0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(tokenOrigen.symbol.take(2), color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(tokenOrigen.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("Balance: ${tokenOrigen.balance.format(4)}", color = Color.Gray, fontSize = 12.sp)
                                            }
                                        }
                                        if (tokens.size > 1) Text("▾", color = Color.Cyan, fontSize = 16.sp)
                                    }
                                }
                                DropdownMenu(expanded = expandirOrigen, onDismissRequest = { expandirOrigen = false }) {
                                    tokens.filter { it.mintAddress != tokenDestino.mintAddress }.forEach { t ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(t.symbol, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(t.balance.format(4), color = Color.Gray, fontSize = 12.sp)
                                                }
                                            },
                                            onClick = {
                                                tokenOrigen = t
                                                expandirOrigen = false
                                                cotizacion = null
                                                montoSalida = null
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SwapVert, null, tint = Color.Cyan, modifier = Modifier.size(32.dp))
                        }

                        Column {
                            Text("HACIA", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = Color.White.copy(0.05f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(40.dp).background(Color.White.copy(0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(tokenDestino.symbol.take(2), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(tokenDestino.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = montoInput,
                            onValueChange = {
                                if (it.all { c -> c.isDigit() || c == '.' }) {
                                    montoInput = it
                                    cotizacion = null
                                    montoSalida = null
                                    errorMsj = null
                                }
                            },
                            label = { Text("Monto ${tokenOrigen.symbol}") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            trailingIcon = {
                                TextButton(onClick = {
                                    montoInput = tokenOrigen.balance.format(6)
                                    cotizacion = null
                                    montoSalida = null
                                }) {
                                    Text("MAX", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Cyan,
                                unfocusedBorderColor = Color.White.copy(0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Button(
                            onClick = { obtenerCotizacion() },
                            enabled = montoInput.isNotEmpty() && !cargandoCotizacion,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f))
                        ) {
                            if (cargandoCotizacion) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Cyan, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("OBTENER COTIZACIÓN", color = Color.White)
                        }

                        if (montoSalida != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Cyan.copy(0.08f), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Enviás", color = Color.Gray, fontSize = 13.sp)
                                    Text("$montoInput ${tokenOrigen.symbol}", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Recibís", color = Color.Gray, fontSize = 13.sp)
                                    Text("${montoSalida!!.format(6)} ${tokenDestino.symbol}", color = Color.Cyan, fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Slippage", color = Color.Gray, fontSize = 12.sp)
                                    Text("0.5%", color = Color.White, fontSize = 12.sp)
                                }
                                if (tokenOrigen.precioUsd > 0) {
                                    val valor = montoNum * tokenOrigen.precioUsd
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Valor aprox", color = Color.Gray, fontSize = 12.sp)
                                        Text("≈ u$ ${valor.format(2)}", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = { paso = 2 },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                            ) {
                                Text("CONTINUAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        if (errorMsj != null) {
                            Text(errorMsj!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("RESUMEN", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            HorizontalDivider(color = Color.White.copy(0.1f))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Enviás", color = Color.Gray)
                                Text("$montoInput ${tokenOrigen.symbol}", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Recibís", color = Color.Gray)
                                Text("${montoSalida?.format(6)} ${tokenDestino.symbol}", color = Color.Cyan, fontWeight = FontWeight.Bold)
                            }
                            if (tokenOrigen.precioUsd > 0) {
                                val valor = montoNum * tokenOrigen.precioUsd
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Valor aprox", color = Color.Gray)
                                    Text("≈ u$ ${valor.format(2)}", color = Color.White)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("COMISIONES", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            HorizontalDivider(color = Color.White.copy(0.1f))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Red Solana (gas)", color = Color.Gray, fontSize = 13.sp)
                                Text("~0.000005 SOL", color = Color.White, fontSize = 13.sp)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("LutiWallet (0.1%)", color = Color.Gray, fontSize = 13.sp)
                                Text("~${"%.6f".format(comisionLutiSol)} SOL", color = Color.White, fontSize = 13.sp)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Slippage Jupiter", color = Color.Gray, fontSize = 13.sp)
                                Text("0.5%", color = Color.White, fontSize = 13.sp)
                            }
                            HorizontalDivider(color = Color.White.copy(0.1f))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Total comisiones", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("~${"%.6f".format(totalComisionSol)} SOL", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        if (ejecutandoSwap) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = Color.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Procesando swap...", color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            ConfirmacionSlider(
                                onConfirmado = {
                                    val quote = cotizacion ?: return@ConfirmacionSlider
                                    scope.launch {
                                        ejecutandoSwap = true
                                        errorMsj = null
                                        try {
                                            // Cobrar comisión LutiWallet a tesorería antes del swap
                                            if (comisionLutiSol >= 0.000001) {
                                                val txCom = SolanaManager.sendSolReal(
                                                    fraseActiva, DIRECCION_TESORERIA, comisionLutiSol
                                                )
                                                if (txCom == null) {
                                                    errorMsj = "Error al procesar comisión. Verificá tu saldo de SOL."
                                                    paso = 1
                                                    return@launch
                                                }
                                                delay(500)
                                            }

                                            val txId = JupiterManager.ejecutarSwap(
                                                frase = fraseActiva,
                                                direccionWallet = direccionActiva,
                                                cotizacion = quote
                                            )
                                            if (txId != null) {
                                                Toast.makeText(context, "¡Swap exitoso!", Toast.LENGTH_LONG).show()
                                                // Guardar en historial
                                                val now = Calendar.getInstance().time
                                                val nuevaTx = Transaccion(
                                                    tipo = "Swap ${tokenOrigen.symbol} → ${tokenDestino.symbol}",
                                                    monto = montoInput,
                                                    fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now),
                                                    hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                                                    esEnvio = true,
                                                    de = tokenOrigen.symbol,
                                                    para = tokenDestino.symbol,
                                                    estado = "Éxito",
                                                    direccionOwner = direccionActiva,
                                                    tokenSymbol = tokenOrigen.symbol,
                                                    tokenSymbolDestino = tokenDestino.symbol,
                                                    montoDestino = montoSalida?.format(6) ?: "",
                                                    esSwap = true
                                                )
                                                historialTotal.add(0, nuevaTx)
                                                guardarHistorialEnDisco(context, historialTotal)
                                                agregarTransaccionFirebase(nuevaTx)
                                                onSwapExitoso()
                                            } else {
                                                errorMsj = "El swap falló. Intentá de nuevo."
                                                paso = 1
                                            }
                                        } catch (e: Exception) {
                                            errorMsj = "Error: ${e.localizedMessage}"
                                            paso = 1
                                        } finally {
                                            ejecutandoSwap = false
                                        }
                                    }
                                }
                            )
                        }

                        if (errorMsj != null) {
                            Text(errorMsj!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}