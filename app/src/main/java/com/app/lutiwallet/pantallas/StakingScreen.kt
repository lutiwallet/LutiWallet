package com.app.lutiwallet.pantallas

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.lutiwallet.modelo.Token
import com.app.lutiwallet.modelo.Transaccion
import com.app.lutiwallet.modelo.agregarTransaccionFirebase
import com.app.lutiwallet.utils.COMISION_PORCENTAJE
import com.app.lutiwallet.utils.DIRECCION_TESORERIA
import com.app.lutiwallet.utils.ImagenToken
import com.app.lutiwallet.utils.JupiterManager
import com.app.lutiwallet.utils.format
import com.app.lutiwallet.viewmodel.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit



private data class ProtocoloStaking(
    val nombre: String,
    val subtitulo: String,
    val mint: String,
    val symbol: String,
    val decimals: Int = 9,
    val colorAccent: Color,
    val urlApy: String,
    val apyFallback: Double
)

private val PROTOCOLOS_STAKING = listOf(
    ProtocoloStaking(
        nombre = "Marinade",
        subtitulo = "El protocolo más usado en Solana",
        mint = "mSoLzYCFHTOwPxwo9UVwQLFzvm3xEMpj5RZe5eWJQE",
        symbol = "mSOL",
        colorAccent = Color(0xFF00BCD4),
        urlApy = "https://api.marinade.finance/msol/apy/1y",
        apyFallback = 7.5
    ),
    ProtocoloStaking(
        nombre = "Jito",
        subtitulo = "Con MEV rewards incluidos",
        mint = "J1toso1uCk3RLmjorhTtrVwY9HJ7X8V9yYac6Y7kGCPn",
        symbol = "JitoSOL",
        colorAccent = Color(0xFF4CAF50),
        urlApy = "https://kobe.mainnet.jito.network/api/v1/returns",
        apyFallback = 8.0
    ),
    ProtocoloStaking(
        nombre = "BlazeStake",
        subtitulo = "Staking descentralizado",
        mint = "bSo13r4TkiE4KumL71LsHTPpL2euBYLFx6h9HP3piy1",
        symbol = "bSOL",
        colorAccent = Color(0xFFFF9800),
        urlApy = "https://stake.solblaze.org/api/v1/apy",
        apyFallback = 7.0
    )
)

private val httpStaking = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()


private suspend fun fetchApy(protocolo: ProtocoloStaking): Double = withContext(Dispatchers.IO) {
    try {
        val body = httpStaking.newCall(
            Request.Builder().url(protocolo.urlApy).get().build()
        ).execute().body?.string() ?: return@withContext protocolo.apyFallback

        if (body.isEmpty()) return@withContext protocolo.apyFallback


        try {
            val json = JSONObject(body)
            val raw = json.optDouble("apy", Double.NaN).takeIf { !it.isNaN() }
                ?: json.optDouble("value", Double.NaN).takeIf { !it.isNaN() }
                ?: json.optDouble("average_apy", Double.NaN).takeIf { !it.isNaN() }
                ?: json.optDouble("annualized_apy", Double.NaN).takeIf { !it.isNaN() }
            if (raw != null) return@withContext if (raw < 1.0) raw * 100.0 else raw
        } catch (e: Exception) { Log.w("StakingScreen", "APY JSON parse: ${e.message}") }


        val raw = body.trim().toDoubleOrNull() ?: return@withContext protocolo.apyFallback
        if (raw < 1.0) raw * 100.0 else raw

    } catch (e: Exception) {
        Log.w("StakingScreen", "APY fetch fallido para ${protocolo.nombre}: ${e.message}")
        protocolo.apyFallback
    }
}



@Composable
fun StakingScreen(
    viewModel: WalletViewModel,
    direccionActiva: String,
    fraseActiva: String,
    textos: Map<String, String> = emptyMap()
) {
    val scope = rememberCoroutineScope()
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val saldo by viewModel.saldo.collectAsStateWithLifecycle()
    val precioSolUsd by viewModel.precioUsd.collectAsStateWithLifecycle()

    val apys = remember { mutableStateMapOf<String, Double>() }
    var protocoloActivo by remember { mutableStateOf<ProtocoloStaking?>(null) }
    var modoDialog by remember { mutableStateOf("stakear") }

    LaunchedEffect(Unit) {
        PROTOCOLOS_STAKING.forEach { protocolo ->
            scope.launch {
                apys[protocolo.mint] = fetchApy(protocolo)
            }
        }
    }


    val tokensPorProtocolo = remember(tokens) {
        PROTOCOLOS_STAKING.associateWith { p -> tokens.firstOrNull { it.mintAddress == p.mint } }
    }
    val totalStakeadoUsd = tokensPorProtocolo.values.sumOf { it?.valorUsd ?: 0.0 }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))

        Text(
            textos["staking"] ?: "STAKING LÍQUIDO",
            fontSize = 11.sp, fontWeight = FontWeight.Black,
            color = Color.Gray, letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tus SOL generan rendimientos automáticos",
            fontSize = 13.sp, color = Color.White.copy(0.6f)
        )
        Spacer(Modifier.height(16.dp))


        if (totalStakeadoUsd > 0.01) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D2B0D), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Savings, null,
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Total stakeado", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            "≈ u$ ${totalStakeadoUsd.format(2)}",
                            color = Color(0xFF00C853),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        // Estimación de renta anual promedio
                        val apyPromedio = tokensPorProtocolo.entries
                            .filter { (_, t) -> (t?.valorUsd ?: 0.0) > 0 }
                            .mapNotNull { (p, t) ->
                                val apy = apys[p.mint] ?: p.apyFallback
                                (t?.valorUsd ?: 0.0) * apy / 100.0
                            }.sum()
                        if (apyPromedio > 0) {
                            Text(
                                "≈ +u$ ${"%.2f".format(apyPromedio)}/año estimados",
                                color = Color(0xFF00C853).copy(0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(PROTOCOLOS_STAKING) { protocolo ->
                ProtocoloCard(
                    protocolo = protocolo,
                    apy = apys[protocolo.mint] ?: protocolo.apyFallback,
                    tokenStakeado = tokensPorProtocolo[protocolo],
                    saldoSol = saldo,
                    onStakear = { protocoloActivo = protocolo; modoDialog = "stakear" },
                    onDesestakear = { protocoloActivo = protocolo; modoDialog = "desestakear" }
                )
            }


            item {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color.White.copy(0.03f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info, null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "El retiro es instantáneo. Los APYs son estimados y pueden variar.",
                            color = Color.Gray, fontSize = 10.sp
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }


    if (protocoloActivo != null) {
        StakingDialog(
            protocolo = protocoloActivo!!,
            modo = modoDialog,
            saldoSol = saldo,
            precioSolUsd = precioSolUsd,
            tokenStakeado = tokensPorProtocolo[protocoloActivo!!],
            fraseActiva = fraseActiva,
            direccionActiva = direccionActiva,
            apy = apys[protocoloActivo!!.mint] ?: protocoloActivo!!.apyFallback,
            tokens = tokens,
            onDismiss = { protocoloActivo = null },
            onExito = {
                protocoloActivo = null
                viewModel.refreshTokens(direccionActiva)
            }
        )
    }
}



@Composable
private fun ProtocoloCard(
    protocolo: ProtocoloStaking,
    apy: Double,
    tokenStakeado: Token?,
    saldoSol: Double,
    onStakear: () -> Unit,
    onDesestakear: () -> Unit
) {
    val tieneStake = (tokenStakeado?.balance ?: 0.0) > 0.000001

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Header del protocolo
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImagenToken(
                symbol = protocolo.symbol,
                mintAddress = protocolo.mint,
                logoUrl = "",
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(protocolo.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(protocolo.subtitulo, color = Color.Gray, fontSize = 11.sp)
            }
            Surface(
                color = Color(0xFF00C853).copy(0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "${"%.1f".format(apy)}% APY",
                    color = Color(0xFF00C853),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }


        if (tieneStake && tokenStakeado != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(protocolo.colorAccent.copy(0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle, null,
                        tint = protocolo.colorAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${tokenStakeado.balance.format(4)} ${protocolo.symbol}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (tokenStakeado.valorUsd > 0) {
                            Text(
                                "≈ u$ ${tokenStakeado.valorUsd.format(2)}",
                                color = Color.Gray, fontSize = 11.sp
                            )
                        }
                    }

                    if (tokenStakeado.valorUsd > 0 && apy > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "+u$ ${"%.2f".format(tokenStakeado.valorUsd * apy / 100.0)}/año",
                                color = Color(0xFF00C853),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("est. anual", color = Color.Gray, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tieneStake) {
                OutlinedButton(
                    onClick = onDesestakear,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.ArrowDownward, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Desestakear", fontSize = 12.sp)
                }
            }
            Button(
                onClick = onStakear,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = protocolo.colorAccent),
                enabled = saldoSol > 0.001
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward, null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (tieneStake) "Stakear más" else "Stakear SOL",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}



@Composable
private fun StakingDialog(
    protocolo: ProtocoloStaking,
    modo: String,
    saldoSol: Double,
    precioSolUsd: Double,
    tokenStakeado: Token?,
    fraseActiva: String,
    direccionActiva: String,
    apy: Double,
    tokens: List<Token>,
    onDismiss: () -> Unit,
    onExito: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val stakearMode = modo == "stakear"

    val solMint = "So11111111111111111111111111111111111111112"
    val mintOrigen = if (stakearMode) solMint else protocolo.mint
    val mintDestino = if (stakearMode) protocolo.mint else solMint
    val balanceDisponible = if (stakearMode) saldoSol else (tokenStakeado?.balance ?: 0.0)
    val symbolOrigen = if (stakearMode) "SOL" else protocolo.symbol
    val symbolDestino = if (stakearMode) protocolo.symbol else "SOL"
    val decimalesOrigen = if (stakearMode) 9 else protocolo.decimals

    var monto by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var estimacionSalida by remember { mutableStateOf<String?>(null) }

    // Actualizar estimación al cambiar el monto
    LaunchedEffect(monto) {
        val cantidad = monto.toDoubleOrNull() ?: run { estimacionSalida = null; return@LaunchedEffect }
        if (cantidad <= 0) { estimacionSalida = null; return@LaunchedEffect }
        try {
            val lamports = (cantidad * Math.pow(10.0, decimalesOrigen.toDouble())).toLong()
            val quote = withContext(Dispatchers.IO) {
                JupiterManager.obtenerCotizacion(mintOrigen, mintDestino, lamports)
            }
            if (quote != null) {
                val outRaw = quote.optLong("outAmount", 0L)
                val decimalesDestino = if (stakearMode) protocolo.decimals else 9
                val salida = outRaw / Math.pow(10.0, decimalesDestino.toDouble())
                estimacionSalida = "Recibirás ≈ ${"%.4f".format(salida)} $symbolDestino"
            } else {
                estimacionSalida = null
            }
        } catch (e: Exception) {
            estimacionSalida = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!procesando) onDismiss() },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImagenToken(
                    symbol = protocolo.symbol,
                    mintAddress = protocolo.mint,
                    logoUrl = "",
                    size = 28.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (stakearMode) "Stakear con ${protocolo.nombre}"
                    else "Desestakear de ${protocolo.nombre}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Chip de APY (solo al stakear)
                if (stakearMode) {
                    Surface(
                        color = Color(0xFF00C853).copy(0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.TrendingUp, null, tint = Color(0xFF00C853), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "APY estimado: ${"%.1f".format(apy)}%  •  Retiro instantáneo",
                                color = Color(0xFF00C853),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    "Disponible: ${"%.4f".format(balanceDisponible)} $symbolOrigen",
                    color = Color.Gray, fontSize = 12.sp
                )

                OutlinedTextField(
                    value = monto,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d*$"))) {
                            monto = it; error = null
                        }
                    },
                    label = { Text("Cantidad de $symbolOrigen") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { monto = balanceDisponible.format(4) }) {
                            Text("MAX", color = protocolo.colorAccent, fontSize = 11.sp)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = protocolo.colorAccent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Estimación de salida
                if (estimacionSalida != null) {
                    Surface(
                        color = protocolo.colorAccent.copy(0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                estimacionSalida!!,
                                color = protocolo.colorAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val cantidadSol = monto.toDoubleOrNull() ?: 0.0
                            if (stakearMode && cantidadSol > 0 && precioSolUsd > 0) {
                                Text(
                                    "≈ u$ ${"%.2f".format(cantidadSol * precioSolUsd)} — mismo valor en USD",
                                    color = protocolo.colorAccent.copy(0.7f),
                                    fontSize = 10.sp
                                )
                                Text(
                                    "El ${protocolo.symbol} vale más que 1 SOL porque acumula el APY",
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }


                if (stakearMode && precioSolUsd > 0) {
                    val cantidad = monto.toDoubleOrNull() ?: 0.0
                    if (cantidad > 0 && apy > 0) {
                        val rendUsd = cantidad * precioSolUsd * apy / 100.0
                        Text(
                            "Rendimiento anual ≈ u$ ${"%.2f".format(rendUsd)}",
                            color = Color(0xFF00C853),
                            fontSize = 11.sp
                        )
                    }
                }

                if (error != null) {
                    Text(error!!, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cantidad = monto.toDoubleOrNull()
                    when {
                        cantidad == null || cantidad <= 0 -> error = "Ingresá un monto válido"
                        cantidad > balanceDisponible -> error = "Saldo insuficiente"
                        else -> {
                            scope.launch {
                                procesando = true
                                error = null
                                try {
                                    val lamports = (cantidad * Math.pow(10.0, decimalesOrigen.toDouble())).toLong()

                                    // Cotización
                                    val cotizacion = withContext(Dispatchers.IO) {
                                        JupiterManager.obtenerCotizacion(mintOrigen, mintDestino, lamports)
                                    }
                                    if (cotizacion == null) {
                                        error = "No se pudo obtener cotización. Intentá con menos monto."
                                        return@launch
                                    }

                                    // Comisión en SOL (igual que en SwapScreen)
                                    val precioToken = if (stakearMode) (precioSolUsd.takeIf { it > 0 } ?: 1.0) else (tokenStakeado?.precioUsd ?: 0.0)
                                    val precioSol = tokens.firstOrNull { it.esSOL }?.precioUsd ?: precioSolUsd
                                    if (precioToken > 0 && precioSol > 0) {
                                        val comisionSol = (cantidad * precioToken * COMISION_PORCENTAJE) / precioSol
                                        if (comisionSol >= 0.000001) {
                                            withContext(Dispatchers.IO) {
                                                com.app.lutiwallet.SolanaManager.sendSolReal(
                                                    fraseActiva, DIRECCION_TESORERIA, comisionSol
                                                )
                                            }
                                            delay(800)
                                        }
                                    }


                                    val txId = withContext(Dispatchers.IO) {
                                        JupiterManager.ejecutarSwap(fraseActiva, direccionActiva, cotizacion)
                                    }

                                    if (txId != null) {
                                        // Guardar en historial
                                        val now = Calendar.getInstance().time
                                        val nuevaTx = Transaccion(
                                            tipo = if (stakearMode) "Staking" else "Unstaking",
                                            monto = "${"%.4f".format(cantidad)} $symbolOrigen",
                                            fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now),
                                            hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                                            esEnvio = true,
                                            de = direccionActiva,
                                            para = protocolo.nombre,
                                            estado = "Éxito",
                                            direccionOwner = direccionActiva
                                        )
                                        agregarTransaccionFirebase(nuevaTx)
                                        onExito()
                                    } else {
                                        error = "Error al ejecutar. Revisá tu saldo de SOL para gas."
                                    }
                                } catch (e: Exception) {
                                    error = "Error: ${e.localizedMessage}"
                                } finally {
                                    procesando = false
                                }
                            }
                        }
                    }
                },
                enabled = !procesando,
                colors = ButtonDefaults.buttonColors(containerColor = protocolo.colorAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (procesando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (procesando) "Procesando..."
                    else if (stakearMode) "STAKEAR" else "DESESTAKEAR",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!procesando) onDismiss() }) {
                Text("CANCELAR", color = Color.Gray)
            }
        }
    )
}
