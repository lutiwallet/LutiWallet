package com.app.lutiwallet.pantallas

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.app.lutiwallet.modelo.Token
import com.app.lutiwallet.modelo.TOKENS_POPULARES
import com.app.lutiwallet.modelo.Transaccion
import com.app.lutiwallet.modelo.cargarHistorialDesdeDisco
import com.app.lutiwallet.utils.*
import kotlinx.coroutines.delay
import com.app.lutiwallet.viewmodel.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.app.lutiwallet.SolanaManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(
    viewModel: WalletViewModel,
    direccionActiva: String,
    fraseActiva: String,
    textos: Map<String, String> = emptyMap(),
    onEnviarToken: (Token) -> Unit,
    onRecibirToken: (Token) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }
    val clipboardManager = LocalClipboardManager.current

    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val totalUsd by viewModel.totalUsd.collectAsStateWithLifecycle()
    val precioDolarCrypto by viewModel.precioDolarCrypto.collectAsStateWithLifecycle()
    val cargandoTokens by viewModel.cargandoTokens.collectAsStateWithLifecycle()
    var tokenEnEsperaDeQR by remember { mutableStateOf<Token?>(null) }

    val historialTotal = remember {
        mutableStateListOf<Transaccion>().apply {
            addAll(cargarHistorialDesdeDisco(context))
        }
    }

    var busqueda by remember { mutableStateOf("") }
    var resultadosBusqueda by remember { mutableStateOf<List<Token>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var tokenParaSwap by remember { mutableStateOf<Token?>(null) }
    var tokenParaRecibir by remember { mutableStateOf<Token?>(null) }
    var tokenParaEnviar by remember { mutableStateOf<Token?>(null) }
    var tokenParaSeleccionarEnvio by remember { mutableStateOf<Token?>(null) }
    var mostrarCamaraEnvio by remember { mutableStateOf(false) }
    var destinoEscaneado by remember { mutableStateOf("") }
    var fiatSeleccionada by remember { mutableStateOf(prefsSeguras.getString("fiat_pref", "ARS") ?: "ARS") }
    var favoritosActuales by remember { mutableStateOf(obtenerFavoritos(context)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val factor = obtenerFactorFiat(fiatSeleccionada, precioDolarCrypto)
    val totalFiat = totalUsd * factor

    // Función centralizada para manejar favoritos
    fun toggleFavoritoConToken(token: Token) {
        toggleFavorito(context, token.mintAddress)
        guardarTokenFavorito(context, token)
        favoritosActuales = obtenerFavoritos(context)
    }

    LaunchedEffect(direccionActiva) {
        viewModel.refreshTokens(direccionActiva)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text(textos["mis_tokens"] ?: "MIS TOKENS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Text("$ ${totalFiat.format(2)} $fiatSeleccionada", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("≈ u$ ${totalUsd.format(2)} USD", fontSize = 13.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = busqueda,
            onValueChange = { query ->
                busqueda = query
                if (query.length >= 2) {
                    scope.launch {
                        buscando = true
                        resultadosBusqueda = JupiterManager.buscarTokensPorNombre(query)
                        buscando = false
                    }
                } else {
                    resultadosBusqueda = emptyList()
                }
            },
            placeholder = { Text(textos["buscar_placeholder"] ?: "Buscar token por nombre o mint...", color = Color.Gray, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Cyan) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.White.copy(0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        if (cargandoTokens) {
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Cyan, strokeWidth = 2.dp)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busqueda.length >= 2) {
                if (buscando) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (resultadosBusqueda.isNotEmpty()) {
                    item { Text(textos["resultados"] ?: "RESULTADOS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                    items(resultadosBusqueda) { token ->
                        TokenBusquedaItem(
                            token = token,
                            esFavorito = favoritosActuales.contains(token.mintAddress),
                            onToggleFavorito = { toggleFavoritoConToken(token) },
                            onClick = {
                                tokenParaSwap = token
                                busqueda = ""
                                resultadosBusqueda = emptyList()
                            }
                        )
                    }
                } else {
                    item { Text("${textos["sin_resultados"] ?: "Sin resultados para"} \"$busqueda\"", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                }
            } else {
                if (tokens.isNotEmpty()) {
                    item { Text(textos["en_wallet"] ?: "EN TU WALLET", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                    items(tokens) { token ->
                        TokenWalletItem(
                            token = token,
                            esFavorito = favoritosActuales.contains(token.mintAddress),
                            onToggleFavorito = { toggleFavoritoConToken(token) },
                            onEnviar = {
                                if (token.esSOL) tokenParaEnviar = token
                                else tokenParaSeleccionarEnvio = token
                            },
                            onRecibir = { tokenParaRecibir = token },
                            onSwap = { tokenParaSwap = token },
                            lblRecibir = textos["recibir"] ?: "Recibir",
                            lblEnviar = textos["enviar"] ?: "Enviar",
                            lblSwap = textos["swap"] ?: "Swap"
                        )
                    }
                }

                // Favoritos: populares + custom
                val tokensFavoritosPopulares = TOKENS_POPULARES.filter {
                    favoritosActuales.contains(it.mintAddress) && !it.esSOL
                }
                val tokensFavoritosCustom = obtenerTokensFavoritos(context).filter { custom ->
                    favoritosActuales.contains(custom.mintAddress) &&
                            tokensFavoritosPopulares.none { it.mintAddress == custom.mintAddress }
                }
                val tokensFavoritos = tokensFavoritosPopulares + tokensFavoritosCustom

                if (tokensFavoritos.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(textos["favoritos"] ?: "FAVORITOS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    items(tokensFavoritos) { token ->
                        TokenBusquedaItem(
                            token = token,
                            esFavorito = true,
                            onToggleFavorito = { toggleFavoritoConToken(token) },
                            onClick = { tokenParaSwap = token }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(textos["populares"] ?: "TOKENS POPULARES", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                items(TOKENS_POPULARES.filter { !it.esSOL }) { token ->
                    TokenBusquedaItem(
                        token = token,
                        esFavorito = favoritosActuales.contains(token.mintAddress),
                        onToggleFavorito = { toggleFavoritoConToken(token) },
                        onClick = { tokenParaSwap = token }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // BottomSheet
    if (tokenParaSeleccionarEnvio != null) {
        val token = tokenParaSeleccionarEnvio!!
        ModalBottomSheet(
            onDismissRequest = { tokenParaSeleccionarEnvio = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).background(Color.White.copy(0.2f), CircleShape))
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ImagenToken(symbol = token.symbol, mintAddress = token.mintAddress, logoUrl = token.logoUrl, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Enviar ${token.symbol}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Balance: ${token.balance.format(6)}", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("¿Cómo querés ingresar la dirección?", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                            .background(Color.White.copy(0.05f), RoundedCornerShape(20.dp))
                            .clickable { tokenParaSeleccionarEnvio = null; mostrarCamaraEnvio = true; tokenEnEsperaDeQR = token }
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.size(64.dp).background(Color.Cyan.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.Cyan, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Escanear QR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text("Apuntá la cámara al código QR", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    Column(
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                            .background(Color.White.copy(0.05f), RoundedCornerShape(20.dp))
                            .clickable { tokenParaSeleccionarEnvio = null; destinoEscaneado = ""; tokenParaEnviar = token }
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.size(64.dp).background(Color.White.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Ingresar manual", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text("Escribí o pegá la dirección", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    // Cámara QR
    if (mostrarCamaraEnvio) {
        Dialog(onDismissRequest = { mostrarCamaraEnvio = false }) {
            Box(modifier = Modifier.size(350.dp).clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
                com.app.lutiwallet.QRScannerView { resultado ->
                    destinoEscaneado = resultado
                    mostrarCamaraEnvio = false
                    tokenParaEnviar = tokenEnEsperaDeQR
                    tokenEnEsperaDeQR = null
                }
                Text("Apuntá al código QR", color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), fontSize = 12.sp)
            }
        }
    }

    // Diálogo RECIBIR
    if (tokenParaRecibir != null) {
        val token = tokenParaRecibir!!
        AlertDialog(
            onDismissRequest = { tokenParaRecibir = null },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("RECIBIR ${token.symbol}", color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Usá tu dirección de Solana para recibir ${token.symbol}", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    val qrBitmap = remember(direccionActiva) { generateQRCode(direccionActiva) }
                    if (qrBitmap != null) {
                        Box(modifier = Modifier.size(200.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(onClick = { clipboardManager.setText(AnnotatedString(direccionActiva)); Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show() },
                        color = Color.Black, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(direccionActiva, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Icon(Icons.Rounded.ContentCopy, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { tokenParaRecibir = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) { Text("LISTO", color = Color.Black) } }
        )
    }

    // Diálogo ENVIAR
    if (tokenParaEnviar != null) {
        val token = tokenParaEnviar!!
        var dest by remember(destinoEscaneado) { mutableStateOf(destinoEscaneado) }
        var mont by remember { mutableStateOf("") }
        var errorMsj by remember { mutableStateOf<String?>(null) }
        var enviando by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!enviando) { tokenParaEnviar = null; destinoEscaneado = "" } },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("ENVIAR ${token.symbol}", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ImagenToken(symbol = token.symbol, mintAddress = token.mintAddress, logoUrl = token.logoUrl, size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(token.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Balance: ${token.balance.format(6)}", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    OutlinedTextField(
                        value = dest, onValueChange = { dest = it; errorMsj = null },
                        label = { Text("Dirección destino") },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (dest.isNotEmpty()) {
                                IconButton(onClick = { dest = ""; errorMsj = null }) {
                                    Icon(Icons.Rounded.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = if (dest == destinoEscaneado && dest.isNotEmpty()) Color.Green else Color.Cyan
                        )
                    )
                    if (dest == destinoEscaneado && dest.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.Green, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dirección escaneada", color = Color.Green, fontSize = 11.sp)
                        }
                    }
                    OutlinedTextField(
                        value = mont, onValueChange = { mont = it; errorMsj = null },
                        label = { Text("Monto ${token.symbol}") },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { TextButton(onClick = { mont = token.balance.format(6) }) { Text("MAX", color = Color.Cyan, fontSize = 11.sp) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Cyan)
                    )
                    if (errorMsj != null) Text(errorMsj!!, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val monto = mont.toDoubleOrNull()
                        when {
                            dest.isBlank() -> errorMsj = "Ingresá una dirección destino"
                            monto == null || monto <= 0 -> errorMsj = "Ingresá un monto válido"
                            monto > token.balance -> errorMsj = "Saldo insuficiente"
                            else -> {
                                scope.launch {
                                    enviando = true
                                    errorMsj = null
                                    try {
                                        val txId = withContext(Dispatchers.IO) {
                                            if (token.esSOL) {
                                                // SOL: cobrar comisión a tesorería primero
                                                val comision = monto * COMISION_PORCENTAJE
                                                val txCom = SolanaManager.sendSolReal(fraseActiva, DIRECCION_TESORERIA, comision)
                                                if (txCom == null) return@withContext null
                                                delay(800)
                                                SolanaManager.sendSolReal(fraseActiva, dest.trim(), monto)
                                            } else {
                                                // SPL: cobrar comisión equivalente en SOL si hay precio disponible
                                                val precioSol = tokens.firstOrNull { it.esSOL }?.precioUsd ?: 0.0
                                                if (token.precioUsd > 0 && precioSol > 0) {
                                                    val comisionSol = (monto * token.precioUsd * COMISION_PORCENTAJE) / precioSol
                                                    if (comisionSol >= 0.000001) {
                                                        val txCom = SolanaManager.sendSolReal(fraseActiva, DIRECCION_TESORERIA, comisionSol)
                                                        if (txCom == null) return@withContext null
                                                        delay(800)
                                                    }
                                                }
                                                SolanaManager.sendSplToken(
                                                    frase = fraseActiva,
                                                    mintAddress = token.mintAddress,
                                                    destino = dest.trim(),
                                                    cantidad = monto,
                                                    decimals = token.decimals
                                                )
                                            }
                                        }
                                        if (txId != null) {
                                            Toast.makeText(context, "¡Envío exitoso!", Toast.LENGTH_LONG).show()
                                            tokenParaEnviar = null
                                            destinoEscaneado = ""
                                            viewModel.refreshTokens(direccionActiva)
                                        } else {
                                            errorMsj = "Error al enviar. Revisá tu saldo de SOL para gas."
                                        }
                                    } catch (e: Exception) {
                                        errorMsj = "Error: ${e.localizedMessage}"
                                    } finally {
                                        enviando = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !enviando,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                ) {
                    if (enviando) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(textos["confirmar"] ?: "CONFIRMAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!enviando) { tokenParaEnviar = null; destinoEscaneado = "" } }) { Text(textos["cancelar"] ?: "CANCELAR", color = Color.Gray) }
            }
        )
    }

    // SWAP
    if (tokenParaSwap != null) {
        SwapScreen(
            viewModel = viewModel,
            tokenDestino = tokenParaSwap!!,
            tokens = tokens,
            fraseActiva = fraseActiva,
            direccionActiva = direccionActiva,
            historialTotal = historialTotal,
            onDismiss = { tokenParaSwap = null },
            onSwapExitoso = { tokenParaSwap = null; viewModel.refreshTokens(direccionActiva) }
        )
    }
}

@Composable
fun TokenWalletItem(
    token: Token,
    esFavorito: Boolean,
    onToggleFavorito: () -> Unit,
    onEnviar: () -> Unit,
    onRecibir: () -> Unit,
    onSwap: () -> Unit,
    lblRecibir: String = "Recibir",
    lblEnviar: String = "Enviar",
    lblSwap: String = "Swap"
) {
    var expandido by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
            .clickable { expandido = !expandido }.padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImagenToken(symbol = token.symbol, mintAddress = token.mintAddress, logoUrl = token.logoUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(token.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (token.esVerificado) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, contentDescription = "Verificado", tint = Color.Cyan, modifier = Modifier.size(14.dp))
                    }
                }
                Text(token.name.take(20), color = Color.Gray, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (token.esSOL) {
                    Icon(Icons.Rounded.PushPin, contentDescription = "Fijado", tint = Color.Cyan, modifier = Modifier.size(16.dp))
                } else {
                    IconButton(onClick = onToggleFavorito, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (esFavorito) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Favorito",
                            tint = if (esFavorito) Color(0xFFFFD700) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Text("${token.balance.format(4)} ${token.symbol}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (token.cambio24h != 0.0) {
                        Spacer(Modifier.width(6.dp))
                        val colorCambio = if (token.cambio24h >= 0) Color(0xFF00C853) else Color(0xFFFF5252)
                        val signo = if (token.cambio24h >= 0) "+" else ""
                        Surface(color = colorCambio.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "$signo${"%.2f".format(token.cambio24h)}%",
                                color = colorCambio,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (token.precioUsd > 0) Text("≈ u$ ${token.valorUsd.format(2)}", color = Color.Cyan, fontSize = 11.sp)
            }
        }
        if (expandido) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRecibir, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f))) {
                    Icon(Icons.Rounded.QrCode, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(lblRecibir, fontSize = 12.sp, color = Color.White)
                }
                Button(onClick = onEnviar, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f))) {
                    Icon(Icons.Rounded.Send, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(lblEnviar, fontSize = 12.sp, color = Color.White)
                }
                Button(onClick = onSwap, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                    Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(14.dp), tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text(lblSwap, fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TokenBusquedaItem(
    token: Token,
    esFavorito: Boolean = false,
    onToggleFavorito: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.03f), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImagenToken(symbol = token.symbol, mintAddress = token.mintAddress, logoUrl = token.logoUrl, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(token.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (token.esVerificado) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Verified, contentDescription = "Verificado", tint = Color.Cyan, modifier = Modifier.size(13.dp))
                }
            }
            Text(token.name.take(30), color = Color.Gray, fontSize = 11.sp)
        }
        if (!token.esSOL) {
            IconButton(onClick = onToggleFavorito, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (esFavorito) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = "Favorito",
                    tint = if (esFavorito) Color(0xFFFFD700) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Rounded.SwapHoriz, null, tint = Color.Cyan, modifier = Modifier.size(20.dp))
    }
}