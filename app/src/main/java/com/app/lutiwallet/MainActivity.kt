package com.app.lutiwallet

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.lutiwallet.LutiCryptoManager
import com.app.lutiwallet.SolanaUtils
import com.app.lutiwallet.pantallas.BilleteraScreen
import com.app.lutiwallet.pantallas.ChatTab
import com.app.lutiwallet.pantallas.ConfigScreen
import com.app.lutiwallet.pantallas.ConversionTab
import com.app.lutiwallet.pantallas.CrearPasswordScreen
import com.app.lutiwallet.pantallas.LoginScreen
import com.app.lutiwallet.pantallas.RegistroScreen
import com.app.lutiwallet.utils.TEXTOS_IDIOMAS
import com.app.lutiwallet.utils.obtenerPrefsSeguras
import com.app.lutiwallet.viewmodel.WalletViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import android.view.WindowManager
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Token
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.app.lutiwallet.pantallas.StakingScreen
import com.app.lutiwallet.pantallas.TokensScreen

class MainActivity : FragmentActivity() {
    private val viewModel: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }

        val permisos = mutableListOf(android.Manifest.permission.CAMERA).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val faltantes = permisos.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (faltantes.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), 101)
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            UpdateManager.checkForUpdates(this) { url ->
                if (!isFinishing && !isDestroyed) {
                    viewModel.setUpdateUrl(url)
                }
            }
        }, 3000)

        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0B0B)) {
                    WalletApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun WalletApp(viewModel: WalletViewModel) {
    val context = LocalContext.current
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }

    var isAuthenticated by remember { mutableStateOf(false) }
    var creandoPass by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionLocked = remember { mutableStateOf(false) }
    var pausedAt by remember { mutableStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pausedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    if (pausedAt > 0L && System.currentTimeMillis() - pausedAt > 5 * 60 * 1000L) {
                        sessionLocked.value = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(sessionLocked.value) {
        if (sessionLocked.value) {
            isAuthenticated = false
            sessionLocked.value = false
        }
    }
    val listaFrases = remember {
        mutableStateListOf<String>().apply {
            val guardadas = prefsSeguras.getStringSet("lista_frases", emptySet()) ?: emptySet()
            addAll(guardadas.map { txt ->
                try { LutiCryptoManager.decrypt(txt) } catch (e: Exception) { txt }
            })
        }
    }

    var mostrandoRegistro by remember { mutableStateOf(listaFrases.isEmpty()) }
    var mostrarConfig by remember { mutableStateOf(false) }
    var walletActualIdx by remember { mutableStateOf(prefsSeguras.getInt("current_idx", 0)) }
    var idiomaSeleccionado by remember { mutableStateOf(prefsSeguras.getString("idioma_pref", "Español") ?: "Español") }
    var fraseRecienGenerada by remember { mutableStateOf<String?>(null) }
    val updateUrl by viewModel.updateUrl.collectAsStateWithLifecycle()

    val textos = TEXTOS_IDIOMAS[idiomaSeleccionado] ?: TEXTOS_IDIOMAS["Español"]!!

    val passGuardada = remember { prefsSeguras.getString("app_password", "") ?: "" }
    val fraseActiva = if (listaFrases.isNotEmpty() && walletActualIdx < listaFrases.size) listaFrases[walletActualIdx] else ""
    val direccionActiva = remember(fraseActiva) {
        if (fraseActiva.isEmpty()) "No creada" else SolanaUtils.generateAddressFromMnemonic(fraseActiva)
    }

    when {

        mostrandoRegistro -> {
            RegistroScreen(
                listaFrases = listaFrases,
                onWalletCreada = { frase, idx ->
                    walletActualIdx = idx
                    fraseRecienGenerada = frase
                    mostrandoRegistro = false
                    creandoPass = passGuardada.isEmpty()
                },
                onWalletImportada = { idx ->
                    walletActualIdx = idx
                    mostrandoRegistro = false
                    creandoPass = passGuardada.isEmpty()
                }
            )
        }

        !isAuthenticated && !creandoPass -> {
            LoginScreen(
                onLoginExitoso = { isAuthenticated = true },
                textoDesbloquear = textos["desbloquear"] ?: "DESBLOQUEAR",
                textoLogin = textos["login"] ?: "INTRODUCE TU CLAVE"
            )
        }

        creandoPass -> {
            CrearPasswordScreen(onPasswordCreada = {
                creandoPass = false
                isAuthenticated = true
            })
        }

        else -> {
            // Registrar / refrescar el FCM token en Firebase cada vez que el usuario se autentica
            LaunchedEffect(direccionActiva) {
                if (direccionActiva != "No creada") {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val fcmToken = task.result
                            FirebaseDatabase
                                .getInstance("https://lutiwallet-default-rtdb.firebaseio.com/")
                                .getReference("tokens")
                                .child(direccionActiva)
                                .setValue(fcmToken)
                        }
                    }
                }
            }

            var tabSeleccionada by remember { mutableIntStateOf(0) }
            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF0B0B0B)) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Rounded.AccountBalanceWallet, null) },
                            label = { Text(textos["nav_billetera"] ?: "Billetera", fontSize = 10.sp) },
                            selected = tabSeleccionada == 0,
                            onClick = { tabSeleccionada = 0 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Rounded.Token, null) },
                            label = { Text(textos["nav_tokens"] ?: "Tokens", fontSize = 10.sp) },
                            selected = tabSeleccionada == 1,
                            onClick = { tabSeleccionada = 1 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Rounded.CurrencyExchange, null) },
                            label = { Text(textos["nav_conversion"] ?: "Conversión", fontSize = 10.sp) },
                            selected = tabSeleccionada == 2,
                            onClick = { tabSeleccionada = 2 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Rounded.Savings, null) },
                            label = { Text(textos["nav_staking"] ?: "Staking", fontSize = 10.sp) },
                            selected = tabSeleccionada == 3,
                            onClick = { tabSeleccionada = 3 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Rounded.Chat, null) },
                            label = { Text(textos["nav_chat"] ?: "LutiChat", fontSize = 10.sp) },
                            selected = tabSeleccionada == 4,
                            onClick = { tabSeleccionada = 4 }
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                ) {
                    when (tabSeleccionada) {
                        0 -> BilleteraScreen(
                            viewModel = viewModel,
                            direccionActiva = direccionActiva,
                            fraseActiva = fraseActiva,
                            listaFrases = listaFrases,
                            walletActualIdx = walletActualIdx,
                            onCambiarWallet = { idx ->
                                walletActualIdx = idx
                                prefsSeguras.edit().putInt("current_idx", idx).apply()
                            },
                            onNuevaCuenta = { mostrandoRegistro = true },
                            onBloquear = { isAuthenticated = false },
                            onAbrirConfig = { mostrarConfig = true },
                            textos = textos
                        )

                        1 -> TokensScreen(
                            viewModel = viewModel,
                            direccionActiva = direccionActiva,
                            fraseActiva = fraseActiva,
                            textos = textos,
                            onEnviarToken = { tabSeleccionada = 0 },
                            onRecibirToken = { tabSeleccionada = 0 }
                        )

                        2 -> ConversionTab(
                            saldoSol = viewModel.saldo.collectAsStateWithLifecycle().value,
                            precioSolUsd = viewModel.precioUsd.collectAsStateWithLifecycle().value,
                            textos = textos
                        )

                        3 -> StakingScreen(
                            viewModel = viewModel,
                            direccionActiva = direccionActiva,
                            fraseActiva = fraseActiva,
                            textos = textos
                        )

                        4 -> ChatTab(
                            direccionPropia = direccionActiva,
                            alVolverABilletera = { tabSeleccionada = 0 }
                        )
                    }
                }
            }
        }
    }
    if (mostrarConfig) {
        ConfigScreen(
            direccionActiva = direccionActiva,
            idiomaSeleccionado = idiomaSeleccionado,
            onIdiomaChange = { idiomaSeleccionado = it },
            onDismiss = { mostrarConfig = false }
        )
    }

    // Dialog bloqueante de actualización
    if (updateUrl != null) {
        var ojoFrase by remember { mutableStateOf(false) }
        var descargando by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { /* no dismissable */ },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text(
                    "Nueva versión disponible",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Antes de actualizar, asegurate de tener guardada tu frase semilla.",
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(12.dp))
                            .padding(16.dp),
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
                    IconButton(onClick = { ojoFrase = !ojoFrase }) {
                        Icon(
                            if (ojoFrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = Color.Cyan
                        )
                    }
                    Text(
                        "IMPORTANTE: Si alguien obtiene esta frase, tendrá acceso total a tus fondos.",
                        color = Color.Red,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!descargando) {
                            descargando = true
                            UpdateManager.downloadAndInstall(context, updateUrl!!)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (descargando) "Descargando..." else "Ya la guardé — ACTUALIZAR",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    if (fraseRecienGenerada != null) {
        AlertDialog(
            onDismissRequest = { fraseRecienGenerada = null },
            title = { Text("Guarda tu Frase") },
            text = {
                SelectionContainer {
                    Text(fraseRecienGenerada!!, color = Color.Cyan)
                }
            },
            confirmButton = {
                Button(onClick = { fraseRecienGenerada = null }) {
                    Text("OK")
                }
            }
        )
    }
}