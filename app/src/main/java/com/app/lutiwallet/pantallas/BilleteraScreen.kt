package com.app.lutiwallet.pantallas

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.app.lutiwallet.modelo.Transaccion
import com.app.lutiwallet.modelo.agregarTransaccionFirebase
import com.app.lutiwallet.modelo.cargarHistorialFirebase
import com.app.lutiwallet.modelo.guardarHistorialEnDisco
import com.app.lutiwallet.utils.*
import com.app.lutiwallet.viewmodel.WalletViewModel
import com.app.lutiwallet.SolanaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BilleteraScreen(
    viewModel: WalletViewModel,
    direccionActiva: String,
    fraseActiva: String,
    listaFrases: List<String>,
    walletActualIdx: Int,
    onCambiarWallet: (Int) -> Unit,
    onNuevaCuenta: () -> Unit,
    onBloquear: () -> Unit,
    onAbrirConfig: () -> Unit,
    textos: Map<String, String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }

    val saldo by viewModel.saldo.collectAsStateWithLifecycle()
    val precioUsd by viewModel.precioUsd.collectAsStateWithLifecycle()
    val precioDolarCrypto by viewModel.precioDolarCrypto.collectAsStateWithLifecycle()
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val totalUsd by viewModel.totalUsd.collectAsStateWithLifecycle()
    val cambioPortfolio by viewModel.cambioPortfolio24h.collectAsStateWithLifecycle()
    val sinConexion by viewModel.sinConexion.collectAsStateWithLifecycle()

    var saldoVisible by remember { mutableStateOf(prefsSeguras.getBoolean("saldo_visible", true)) }
    var fiatSeleccionada by remember { mutableStateOf(prefsSeguras.getString("fiat_pref", "ARS") ?: "ARS") }
    var menuExpandido by remember { mutableStateOf(false) }
    var menuMonedaExpandido by remember { mutableStateOf(false) }
    var mostrarOpcionesEnvio by remember { mutableStateOf(false) }
    var mostrarCamara by remember { mutableStateOf(false) }
    var mostrarDialogoRecibir by remember { mutableStateOf(false) }
    var mostrarDialogoEnvio by remember { mutableStateOf(false) }
    var destinoInput by remember { mutableStateOf("") }
    var txSeleccionada by remember { mutableStateOf<Transaccion?>(null) }

    val historialTotal = remember {
        mutableStateListOf<Transaccion>().apply {
            addAll(com.app.lutiwallet.modelo.cargarHistorialDesdeDisco(context))
        }
    }
    val historialFiltrado = historialTotal.filter { it.direccionOwner == direccionActiva }
    val factor = obtenerFactorFiat(fiatSeleccionada, precioDolarCrypto)
    val totalFiat = totalUsd * factor

    LaunchedEffect(direccionActiva) {
        if (direccionActiva != "No creada") {
            cargarHistorialFirebase(direccionActiva) { txsFirebase ->
                val clavesLocales = historialTotal.map { "${it.fecha}_${it.hora}_${it.tipo}_${it.monto}" }.toSet()
                txsFirebase.filter { "${it.fecha}_${it.hora}_${it.tipo}_${it.monto}" !in clavesLocales }.forEach {
                    historialTotal.add(it)
                }
                historialTotal.sortByDescending { it.timestamp }
                guardarHistorialEnDisco(context, historialTotal)
            }
            viewModel.refreshAll(context, direccionActiva, historialTotal) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

        if (sinConexion) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                color = Color(0xFF332200),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = Color(0xFFFFAA00),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Sin conexión a internet",
                            color = Color(0xFFFFAA00),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Mostrando datos guardados. Conectate para actualizar.",
                            color = Color(0xFFFFAA00).copy(0.75f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 15.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column(Modifier.clickable { if (listaFrases.isNotEmpty()) menuExpandido = true }) {
                Text("LUTIWALLET", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                if (listaFrases.isNotEmpty()) Text("Cuenta ${walletActualIdx + 1} ▾", fontSize = 11.sp, color = Color.Cyan)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onAbrirConfig() },
                    modifier = Modifier.background(Color.White.copy(0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    Surface(
                        onClick = { menuMonedaExpandido = true },
                        color = Color.White.copy(0.05f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(0.1f))
                    ) {
                        Text(
                            fiatSeleccionada, color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                        )
                    }
                    DropdownMenu(expanded = menuMonedaExpandido, onDismissRequest = { menuMonedaExpandido = false }) {
                        MONEDAS_FIAT.forEach { moneda ->
                            DropdownMenuItem(text = { Text(moneda) }, onClick = {
                                fiatSeleccionada = moneda
                                prefsSeguras.edit().putString("fiat_pref", moneda).apply()
                                menuMonedaExpandido = false
                            })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.refreshAll(context, direccionActiva, historialTotal) {} },
                    modifier = Modifier.background(Color.White.copy(0.05f), CircleShape)
                ) {
                    if (cargando) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Cyan, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
            DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                listaFrases.forEachIndexed { index, _ ->
                    DropdownMenuItem(text = { Text("Cuenta ${index + 1}") }, onClick = {
                        onCambiarWallet(index)
                        menuExpandido = false
                    })
                }
                HorizontalDivider(color = Color.White.copy(0.1f))
                DropdownMenuItem(text = { Text("+ Nueva cuenta", color = Color.Cyan) }, onClick = {
                    onNuevaCuenta()
                    menuExpandido = false
                })
                DropdownMenuItem(text = { Text("Bloquear Billetera", color = Color.Red) }, onClick = {
                    onBloquear()
                    menuExpandido = false
                })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF121212))),
                    RoundedCornerShape(28.dp)
                )
                .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(28.dp))
                .padding(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        textos["balance"] ?: "BALANCE TOTAL",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color.Gray, letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (saldoVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null, tint = Color.Gray,
                        modifier = Modifier.size(16.dp).clickable {
                            saldoVisible = !saldoVisible
                            prefsSeguras.edit().putBoolean("saldo_visible", saldoVisible).apply()
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (saldoVisible) "${if (fiatSeleccionada == "ARS") "$" else "$"} ${totalFiat.format(2)} $fiatSeleccionada"
                    else "**** $fiatSeleccionada",
                    fontSize = 33.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.Cyan.copy(0.1f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            if (saldoVisible) "$ ${totalUsd.format(2)} USD" else "**** USD",
                            color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (saldoVisible && cambioPortfolio != 0.0) {
                        val colorCambio = if (cambioPortfolio >= 0) Color(0xFF00C853) else Color(0xFFFF5252)
                        val signo = if (cambioPortfolio >= 0) "+" else ""
                        Surface(color = colorCambio.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (cambioPortfolio >= 0) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                                    contentDescription = null,
                                    tint = colorCambio,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$signo${"%.2f".format(cambioPortfolio)}%",
                                    color = colorCambio,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(color = Color.White.copy(0.05f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            if (saldoVisible) "${saldo.format(4)} SOL" else "**** SOL",
                            color = Color.Gray, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
            if (!mostrarOpcionesEnvio) {
                ActionButton(Modifier.weight(1f), textos["recibir"] ?: "Recibir", Icons.Rounded.QrCode, Color.White) {
                    mostrarDialogoRecibir = true
                }
                ActionButton(Modifier.weight(1f), textos["enviar"] ?: "Enviar", Icons.Rounded.Send, Color.Cyan) {
                    mostrarOpcionesEnvio = true
                }
            } else {
                ActionButton(Modifier.weight(1f), textos["escanear"] ?: "Escanear", Icons.Rounded.PhotoCamera, Color.White) {
                    mostrarCamara = true
                }
                ActionButton(Modifier.weight(1f), textos["retirar"] ?: "Retirar", Icons.Rounded.ArrowUpward, Color.Cyan) {
                    mostrarDialogoEnvio = true
                    mostrarOpcionesEnvio = false
                }
            }
        }
        if (mostrarOpcionesEnvio) {
            TextButton(onClick = { mostrarOpcionesEnvio = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(textos["volver"] ?: "Volver", color = Color.Gray, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (tokens.isNotEmpty()) {
            Text(textos["mis_activos"] ?: "MIS ACTIVOS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            tokens.forEach { token ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ImagenToken(
                        symbol = token.symbol,
                        mintAddress = token.mintAddress,
                        logoUrl = token.logoUrl,
                        size = 36.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(token.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(token.name, color = Color.Gray, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${token.balance.format(4)} ${token.symbol}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (token.precioUsd > 0) Text("u$ ${token.valorUsd.format(2)}", color = Color.Cyan, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(textos["historial"] ?: "HISTORIAL DE ACTIVIDAD", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.5.sp)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            items(historialFiltrado) { tx ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { txSeleccionada = tx },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).background(
                            when {
                                tx.esSwap -> Color.Cyan.copy(0.15f)
                                tx.esEnvio -> Color.White.copy(0.05f)
                                else -> Color.Cyan.copy(0.1f)
                            }, CircleShape
                        ), contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when {
                                tx.esSwap -> Icons.Rounded.SwapHoriz
                                tx.esEnvio -> Icons.Rounded.NorthEast
                                else -> Icons.Rounded.SouthWest
                            },
                            null,
                            tint = when {
                                tx.esSwap -> Color.Cyan
                                tx.esEnvio -> Color.White
                                else -> Color.Cyan
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(tx.tipo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${tx.fecha} • ${tx.hora}", color = Color.Gray, fontSize = 11.sp)
                        if (tx.esSwap && tx.montoDestino.isNotEmpty()) {
                            Text("→ ${tx.montoDestino} ${tx.tokenSymbolDestino}", color = Color.Cyan, fontSize = 11.sp)
                        }
                    }
                    Text(
                        if (tx.esSwap) "${tx.monto} ${tx.tokenSymbol}"
                        else "${if (tx.esEnvio) "-" else "+"} ${tx.monto}",
                        color = if (tx.esEnvio) Color.White else Color.Cyan,
                        fontWeight = FontWeight.Black, fontSize = 15.sp
                    )
                }
            }
        }
    }

    if (mostrarDialogoRecibir) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoRecibir = false },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text(textos["recibir_sol"] ?: "RECIBIR SOL", color = Color.White, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val qrBitmap = remember(direccionActiva) { generateQRCode(direccionActiva) }
                    if (qrBitmap != null) {
                        Box(modifier = Modifier.size(220.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(direccionActiva))
                            Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                        },
                        color = Color.Black, shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(direccionActiva, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Icon(Icons.Default.ContentCopy, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarDialogoRecibir = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                    Text("LISTO", color = Color.Black)
                }
            }
        )
    }

    if (mostrarDialogoEnvio) {
        var dest by remember { mutableStateOf(destinoInput) }
        var mont by remember { mutableStateOf("") }
        var errorMsj by remember { mutableStateOf<String?>(null) }
        var enviando by remember { mutableStateOf(false) }
        var paso by remember { mutableStateOf(1) }

        val montoNum = mont.toDoubleOrNull() ?: 0.0
        val comisionLuti = montoNum * COMISION_PORCENTAJE
        val gasFee = 0.00001
        val totalDescontar = if (montoNum > 0) montoNum + comisionLuti + gasFee else 0.0

        Dialog(
            onDismissRequest = { if (!enviando) { mostrarDialogoEnvio = false; errorMsj = null } },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (!enviando) {
                                if (paso == 2) paso = 1 else { mostrarDialogoEnvio = false; errorMsj = null }
                            }
                        }) {
                            Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (paso == 1) textos["enviar_sol"] ?: "ENVIAR SOL" else textos["confirmar_envio"] ?: "CONFIRMAR ENVÍO",
                            color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp
                        )
                    }

                    if (paso == 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = dest,
                                onValueChange = { dest = it; errorMsj = null },
                                label = { Text("Dirección Destino") },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.Cyan
                                )
                            )
                            OutlinedTextField(
                                value = mont,
                                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) { mont = it; errorMsj = null } },
                                label = { Text("Monto SOL") },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = {
                                        val maxEnviable = saldo - gasFee - (saldo * COMISION_PORCENTAJE)
                                        mont = maxEnviable.coerceAtLeast(0.0).format(6)
                                    }) { Text("MAX", color = Color.Cyan, fontSize = 11.sp) }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.Cyan
                                )
                            )

                            if (montoNum > 0) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Enviás", color = Color.Gray, fontSize = 13.sp)
                                        Text("${montoNum.format(6)} SOL", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Red Solana (gas)", color = Color.Gray, fontSize = 12.sp)
                                        Text("0.00001 SOL", color = Color.White, fontSize = 12.sp)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("LutiWallet (0.1%)", color = Color.Gray, fontSize = 12.sp)
                                        Text("${"%.9f".format(Locale.US, comisionLuti)} SOL", color = Color.White, fontSize = 12.sp)
                                    }
                                    HorizontalDivider(color = Color.White.copy(0.1f))
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Total a descontar", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${totalDescontar.format(9)} SOL", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (precioUsd > 0) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("Valor aprox", color = Color.Gray, fontSize = 12.sp)
                                            Text("≈ u$ ${(montoNum * precioUsd).format(2)}", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            if (errorMsj != null) {
                                Text(errorMsj!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val mFinal = mont.toDoubleOrNull() ?: 0.0
                                    when {
                                        dest.isBlank() -> errorMsj = "Ingresá una dirección destino"
                                        mFinal < MONTO_MINIMO_ENVIO -> errorMsj = "Mínimo de envío: $MONTO_MINIMO_ENVIO SOL"
                                        totalDescontar > saldo -> errorMsj = "Saldo insuficiente"
                                        else -> { errorMsj = null; paso = 2 }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                            ) {
                                Text(textos["continuar"] ?: "CONTINUAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(textos["resumen"] ?: "RESUMEN", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Enviás", color = Color.Gray)
                                    Text("${montoNum.format(6)} SOL", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Para", color = Color.Gray)
                                    Text(
                                        dest.take(8) + "..." + dest.takeLast(6),
                                        color = Color.White, fontWeight = FontWeight.Bold
                                    )
                                }
                                if (precioUsd > 0) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Valor aprox", color = Color.Gray)
                                        Text("≈ u$ ${(montoNum * precioUsd).format(2)}", color = Color.White)
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
                                Text(textos["comisiones"] ?: "COMISIONES", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Red Solana (gas)", color = Color.Gray, fontSize = 13.sp)
                                    Text("0.00001 SOL", color = Color.White, fontSize = 13.sp)
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("LutiWallet (0.1%)", color = Color.Gray, fontSize = 13.sp)
                                    Text("${"%.9f".format(Locale.US, comisionLuti)} SOL", color = Color.White, fontSize = 13.sp)
                                }
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Total comisiones", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${(comisionLuti + gasFee).format(9)} SOL", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (enviando) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(color = Color.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(textos["procesando"] ?: "Procesando envío...", color = Color.Gray, fontSize = 14.sp)
                                }
                            } else {
                                ConfirmacionSlider(
                                    onConfirmado = {
                                        scope.launch {
                                            enviando = true
                                            errorMsj = null
                                            try {
                                                val mFinal = mont.toDoubleOrNull() ?: 0.0
                                                val cFinal = mFinal * COMISION_PORCENTAJE
                                                val cLimpia = "%.9f".format(Locale.US, cFinal).toDouble()

                                                val txCom = withContext(Dispatchers.IO) {
                                                    SolanaManager.sendSolReal(fraseActiva, DIRECCION_TESORERIA, cLimpia)
                                                }
                                                if (txCom != null) {
                                                    delay(1000)
                                                    val txId = withContext(Dispatchers.IO) {
                                                        SolanaManager.sendSolReal(fraseActiva, dest.trim(), mFinal)
                                                    }
                                                    if (txId != null) {
                                                        mostrarDialogoEnvio = false
                                                        Toast.makeText(context, "¡Envío exitoso!", Toast.LENGTH_LONG).show()
                                                        val now = Calendar.getInstance().time
                                                        val nuevaTx = Transaccion(
                                                            tipo = if (dest.trim() == direccionActiva.trim()) "Autotransferencia" else "Enviaste",
                                                            monto = totalDescontar.toBigDecimal().stripTrailingZeros().toPlainString(),
                                                            fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now),
                                                            hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                                                            esEnvio = true,
                                                            de = direccionActiva,
                                                            para = dest.trim(),
                                                            estado = "Éxito",
                                                            direccionOwner = direccionActiva
                                                        )
                                                        historialTotal.add(0, nuevaTx)
                                                        guardarHistorialEnDisco(context, historialTotal)
                                                        agregarTransaccionFirebase(nuevaTx)
                                                        dest = ""; mont = ""; destinoInput = ""
                                                        delay(2500)
                                                        viewModel.refreshAll(context, direccionActiva, historialTotal) {}
                                                    } else {
                                                        errorMsj = "Error en el envío principal."
                                                        paso = 1
                                                    }
                                                } else {
                                                    errorMsj = "La red no pudo procesar la comisión."
                                                    paso = 1
                                                }
                                            } catch (e: Exception) {
                                                errorMsj = "Error: ${e.localizedMessage}"
                                                paso = 1
                                            } finally {
                                                enviando = false
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

    if (txSeleccionada != null) {
        val tx = txSeleccionada!!
        AlertDialog(
            onDismissRequest = { txSeleccionada = null },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("DETALLES", color = Color.Cyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailItem("Monto", "${tx.monto} SOL", Color.Cyan)
                    DetailItem("Fecha", tx.fecha, Color.White)
                    DetailItem("Hora", tx.hora, Color.White)
                    DetailItem("Estado", tx.estado, if (tx.estado == "Éxito") Color.Green else Color.Red)
                    HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("DE:", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    SelectionContainer {
                        Text(tx.de, fontSize = 9.sp, color = Color.White,
                            modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    Text("PARA:", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    SelectionContainer {
                        Text(tx.para, fontSize = 9.sp, color = Color.White,
                            modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { txSeleccionada = null }) { Text("CERRAR", color = Color.Cyan) }
            }
        )
    }

    if (mostrarCamara) {
        Dialog(onDismissRequest = { mostrarCamara = false }) {
            Box(modifier = Modifier.size(350.dp).clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
                com.app.lutiwallet.QRScannerView { resultado ->
                    destinoInput = resultado
                    mostrarCamara = false
                    mostrarDialogoEnvio = true
                }
                Text("Apuntá al código QR", color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionButton(
    mod: Modifier,
    txt: String,
    ico: androidx.compose.ui.graphics.vector.ImageVector,
    col: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = mod.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = col, contentColor = Color.Black)
    ) {
        Icon(ico, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(txt.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}
