package com.app.lutiwallet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoinj.crypto.MnemonicCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth


val VERSION_INSTALADA = "1.0"

@Composable
fun LutiWalletWrapper() {
    val context = LocalContext.current

    var versionRemota by remember { mutableStateOf(VERSION_INSTALADA) }
    var mostrarBloqueo by remember { mutableStateOf(false) }
    var mostrarDialogoSemilla by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val db = FirebaseDatabase.getInstance().getReference("configuracion/version_actual")

        db.get().addOnSuccessListener { snapshot: com.google.firebase.database.DataSnapshot ->

            val vFirebase = snapshot.getValue(String::class.java) ?: VERSION_INSTALADA
            versionRemota = vFirebase


            if (vFirebase != VERSION_INSTALADA) {
                mostrarBloqueo = true
            }
        }
    }

    if (mostrarBloqueo) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Block,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    "ACTUALIZACIÓN OBLIGATORIA",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Tu versión: $VERSION_INSTALADA\nVersión requerida: $versionRemota",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { "https://lutiwallet.com/legal.html" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DESCARGAR AHORA", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = { mostrarDialogoSemilla = true }) {
                    Text("VER MI FRASE SEMILLA (BACKUP)", color = Color.Cyan.copy(0.6f))
                }
            }
        }
    } else {

        ChatTab(
            direccionPropia = "",
            alVolverABilletera = {  }
        )
    }

    if (mostrarDialogoSemilla) {
        DialogoRespaldoEmergencia(onDismiss = { mostrarDialogoSemilla = false })
    }
}

const val DIRECCION_TESORERIA = "8tsgkjVPydnRjbcBGo7TjvmEaaLtRP4mY51MhwF5xLGH"
const val COMISION_PORCENTAJE = 0.001 // 0.1%
const val MONTO_MINIMO_ENVIO = 0.001

fun hashPassword(password: String): String {
    val bytes = password.trim().toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}


fun obtenerPrefsSeguras(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val fileName = "LutiWalletSecretPrefs"

    return try {

        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {

        Log.e("LutiWallet", "Error de desencriptación detectado. Limpiando preferencias corruptas.")


        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply()


        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}

fun Double.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)

fun esPasswordValida(pass: String): Boolean {
    val tieneMayuscula = pass.any { it.isUpperCase() }
    val tieneNumero = pass.any { it.isDigit() }
    val tieneEspecial = pass.any { !it.isLetterOrDigit() }
    return pass.length >= 8 && tieneMayuscula && tieneNumero && tieneEspecial
}

object NotificationHelper {
    private const val CHANNEL_ID = "lutiwallet_notifications"
    fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Transacciones", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

fun generateQRCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) { null }
}

data class Transaccion(
    val tipo: String, val monto: String, val fecha: String, val hora: String,
    val esEnvio: Boolean, val de: String, val para: String,
    val estado: String = "Éxito", val direccionOwner: String
)


fun guardarHistorialEnDisco(context: Context, lista: List<Transaccion>) {
    val prefsSeguras = obtenerPrefsSeguras(context)
    val data = lista.joinToString(";") {
        "${it.tipo}|${it.monto}|${it.fecha}|${it.hora}|${it.esEnvio}|${it.de}|${it.para}|${it.estado}|${it.direccionOwner}"
    }
    prefsSeguras.edit().putString("historial_raw", data).apply()
}

fun cargarHistorialDesdeDisco(context: Context): List<Transaccion> {
    val prefsSeguras = obtenerPrefsSeguras(context)
    val raw = prefsSeguras.getString("historial_raw", "") ?: ""
    if (raw.isEmpty()) return emptyList()
    return raw.split(";").mapNotNull {
        val parts = it.split("|")
        if (parts.size >= 9) {
            Transaccion(parts[0], parts[1], parts[2], parts[3], parts[4].toBoolean(), parts[5], parts[6], parts[7], parts[8])
        } else null
    }
}


fun mostrarBiometria(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear LutiWallet")
        .setSubtitle("Usa tu huella para entrar")
        .setNegativeButtonText("Usar contraseña")
        .build()
    biometricPrompt.authenticate(promptInfo)
}

class WalletViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    init { viewModelScope.launch { delay(1500); _isLoading.value = false } }
}

class MainActivity : FragmentActivity() {
    private val viewModel: WalletViewModel by viewModels()
    companion object {
        var debeAutenticar = mutableStateOf(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    // A partir de este momento, tus listeners de Firebase empezarán a funcionar
                    android.util.Log.d("LutiAuth", "Sesión iniciada: ${it.user?.uid}")
                }
                .addOnFailureListener { e ->
                    // Si esto falla, el Logcat te dirá por qué (generalmente falta de internet)
                    android.util.Log.e("LutiAuth", "Fallo al validar identidad: ${e.message}")
                }
        } else {
            android.util.Log.d("LutiAuth", "Usuario ya identificado: ${auth.currentUser?.uid}")
        }


        val permisosNecesarios = mutableListOf<String>()
        permisosNecesarios.add(android.Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permisosNecesarios.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val permisosAPedir = permisosNecesarios.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (permisosAPedir.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                permisosAPedir.toTypedArray(),
                101
            )
        }


        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            UpdateManager.checkForUpdates(this) { url ->
                if (!isFinishing && !isDestroyed) {

                    Toast.makeText(applicationContext, "Descargando actualización...", Toast.LENGTH_LONG).show()
                    UpdateManager.downloadAndInstall(this, url)
                }
            }
        }, 3000)

        debeAutenticar.value = true
        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }

        setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme()
            ) {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color(0xFF0B0B0B)
                ) {
                    WalletScreen(authStatus = debeAutenticar)
                }
            }
        }
    }
}

@Composable
fun WalletScreen(authStatus: MutableState<Boolean>) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val prefsSeguras = remember { obtenerPrefsSeguras(context) }

    val textosIdiomas = mapOf(
        "Español" to mapOf("titulo" to "CONFIGURACIÓN", "frase" to "Ver Frase Semilla", "idioma" to "Idioma", "pass" to "Contraseña", "login" to "INTRODUCE TU CLAVE", "desbloquear" to "DESBLOQUEAR", "balance" to "BALANCE DISPONIBLE", "historial" to "HISTORIAL", "recibir" to "Recibir", "enviar" to "Enviar"),
        "Inglés" to mapOf("titulo" to "SETTINGS", "frase" to "View Seed", "idioma" to "Language", "pass" to "Password", "login" to "ENTER PASSWORD", "desbloquear" to "UNLOCK", "balance" to "AVAILABLE BALANCE", "historial" to "HISTORY", "recibir" to "Receive", "enviar" to "Send"),
        "Chino" to mapOf("titulo" to "设置", "frase" to "查看助记词", "idioma" to "语言", "pass" to "密码", "login" to "输入密码", "desbloquear" to "解锁", "balance" to "可用余额", "historial" to "历史", "recibir" to "接收", "enviar" to "发送"),
        "Portugués" to mapOf("titulo" to "CONFIGURAÇÕES", "frase" to "Ver Frase", "idioma" to "Idioma", "pass" to "Senha", "login" to "DIGITE SUA SENHA", "desbloquear" to "DESBLOQUEAR", "balance" to "SALDO DISPONÍVEL", "historial" to "HISTÓRICO", "recibir" to "Receber", "enviar" to "Enviar"),
        "Hindi" to mapOf("titulo" to "सेटिंग्स", "frase" to "सीड देखें", "idioma" to "भाषा", "pass" to "पासवर्ड", "login" to "पासवर्ड दर्ज करें", "desbloquear" to "अनलॉक", "balance" to "उपलब्ध शेष", "historial" to "इतिहास", "recibir" to "प्राप्त करें", "enviar" to "भेजें"),
        "Árabe" to mapOf("titulo" to "إعدادات", "frase" to "عرض البذور", "idioma" to "لغة", "pass" to "كلمة المرور", "login" to "أدخل كلمة المرور", "desbloquear" to "فتح", "balance" to "الرصيد", "historial" to "سجل", "recibir" to "يستلم", "enviar" to "إرسال")
    )

    var idiomaSeleccionado by remember { mutableStateOf(prefsSeguras.getString("idioma_pref", "Español") ?: "Español") }
    val t = textosIdiomas[idiomaSeleccionado] ?: textosIdiomas["Español"]!!
    val passGuardada = remember { prefsSeguras.getString("app_password", "") ?: "" }
    var passInput by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(passGuardada.isEmpty()) }
    var creandoPass by remember { mutableStateOf(false) }
    var tabSeleccionada by remember { mutableIntStateOf(0) }
    var saldoVisible by remember { mutableStateOf(prefsSeguras.getBoolean("saldo_visible", true)) }
    var mostrarConfig by remember { mutableStateOf(false) }
    var mostrarFraseSegura by remember { mutableStateOf(false) }
    var passConfirmacion by remember { mutableStateOf("") }
    var mostrarOpcionesEnvio by remember { mutableStateOf(false) }
    var mostrarCamara by remember { mutableStateOf(false) }
    var destinoInput by remember { mutableStateOf("") }

    val permisos = mutableListOf(android.Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permisos.add(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    var walletActualIdx by remember { mutableStateOf(prefsSeguras.getInt("current_idx", 0)) }
    val listaFrases = remember {
        mutableStateListOf<String>().apply {
            val guardadas = prefsSeguras.getStringSet("lista_frases", emptySet()) ?: emptySet()
            val descifradas = guardadas.map { txt ->
                try { LutiCryptoManager.decrypt(txt) } catch (e: Exception) { txt }
            }
            addAll(descifradas)
        }
    }

    var mostrandoRegistro by remember { mutableStateOf(listaFrases.isEmpty()) }
    val fraseActiva = if (listaFrases.isNotEmpty() && walletActualIdx < listaFrases.size) listaFrases[walletActualIdx] else ""
    val direccionActiva = remember(fraseActiva, walletActualIdx) {
        if (fraseActiva.isEmpty()) "No creada" else SolanaUtils.generateAddressFromMnemonic(fraseActiva)
    }

    var saldo by remember(direccionActiva) { mutableDoubleStateOf(prefsSeguras.getFloat("ultimo_saldo_$direccionActiva", 0f).toDouble()) }
    var cargando by remember { mutableStateOf(false) }
    var txSeleccionada by remember { mutableStateOf<Transaccion?>(null) }
    var mostrarDialogoEnvio by remember { mutableStateOf(false) }
    var mostrarDialogoRecibir by remember { mutableStateOf(false) }
    var menuExpandido by remember { mutableStateOf(false) }
    var modoImportar by remember { mutableStateOf(false) }
    var fraseRecienGenerada by remember { mutableStateOf<String?>(null) }
    val monedasFiat = listOf("USD", "ARS", "BRL", "PYG", "EUR", "CNY", "INR", "AED", "GBP", "MXN")
    var fiatSeleccionada by remember { mutableStateOf(prefsSeguras.getString("fiat_pref", "ARS") ?: "ARS") }
    var menuMonedaExpandido by remember { mutableStateOf(false) }
    var precioUsd by remember { mutableDoubleStateOf(0.0) }
    var precioDolarCrypto by remember { mutableDoubleStateOf(0.0) }

    val historialTotal = remember { mutableStateListOf<Transaccion>().apply { addAll(cargarHistorialDesdeDisco(context)) } }
    val historialFiltrado = historialTotal.filter { it.direccionOwner == direccionActiva }

    fun refreshAll() {
        if (direccionActiva == "No creada") return
        scope.launch {
            cargando = true
            try {
                val saldoEnRed = withContext(Dispatchers.IO) { SolanaManager.getBalance(direccionActiva) }
                val saldoAnterior = prefsSeguras.getFloat("ultimo_saldo_$direccionActiva", 0f).toDouble()

                if (saldoEnRed > (saldoAnterior + 0.000001)) {
                    val diff = (saldoEnRed - saldoAnterior)
                    val now = Calendar.getInstance().time
                    val nuevaTx = Transaccion(
                        "Recibiste", diff.format(9),
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now),
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                        false, "Remitente Externo", direccionActiva, "Éxito", direccionActiva
                    )
                    historialTotal.add(0, nuevaTx)
                    guardarHistorialEnDisco(context, historialTotal)
                    NotificationHelper.showNotification(context, "Fondos Recibidos", "Has recibido ${diff.format(4)} SOL")
                }

                saldo = saldoEnRed
                prefsSeguras.edit().putFloat("ultimo_saldo_$direccionActiva", saldoEnRed.toFloat()).apply()

                precioUsd = withContext(Dispatchers.IO) { PriceManager.getSolPriceInUsd() }
                precioDolarCrypto = withContext(Dispatchers.IO) { PriceManager.getDolarCryptoArs() }
            } catch (e: Exception) {
                Log.e("LutiWallet", "Error de red: ${e.message}")
                Toast.makeText(context, "Error al actualizar saldos. Revisá tu conexión.", Toast.LENGTH_SHORT).show()
            } finally {
                cargando = false
            }
        }
    }

    if (!isAuthenticated && !creandoPass) {
        LaunchedEffect(Unit) {
            if (passGuardada.isNotEmpty() && activity != null) {
                mostrarBiometria(activity) {
                    isAuthenticated = true
                    authStatus.value = false
                    passInput = ""
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(30.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Lock, null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(20.dp))
            Text("LUTIWALLET", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(t["login"] ?: "INTRODUCE TU CLAVE DE APP", fontSize = 10.sp, color = Color.Cyan, letterSpacing = 2.sp)
            Spacer(Modifier.height(30.dp))

            OutlinedTextField(
                value = passInput, onValueChange = { passInput = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    val claveEnDisco = prefsSeguras.getString("app_password", "") ?: ""
                    val passwordHaseada = hashPassword(passInput)
                    if (passwordHaseada == claveEnDisco) {
                        isAuthenticated = true
                        authStatus.value = false
                        passInput = ""
                    } else {
                        Toast.makeText(context, "Contraseña Incorrecta", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 15.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) { Text(t["desbloquear"] ?: "DESBLOQUEAR", color = Color.Black, fontWeight = FontWeight.Bold) }

            if (passGuardada.isNotEmpty() && activity != null) {
                Spacer(Modifier.height(20.dp))
                IconButton(onClick = {
                    mostrarBiometria(activity) {
                        isAuthenticated = true
                        authStatus.value = false
                        passInput = ""
                    }
                }) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = "Biometría", tint = Color.Cyan, modifier = Modifier.size(48.dp))
                }
            }
        }
    } else if (creandoPass) {
        Column(Modifier.fillMaxSize().padding(30.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Security, null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Seguridad de la Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("8+ caracteres, Mayúscula, Número y Símbolo", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = passInput, onValueChange = { passInput = it },
                label = { Text("Nueva Contraseña de Acceso") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
            val valida = esPasswordValida(passInput)
            Button(
                onClick = {
                    if (valida) {
                        val hashAGuardar = hashPassword(passInput)
                        prefsSeguras.edit().putString("app_password", hashAGuardar).apply()
                        creandoPass = false
                        isAuthenticated = true
                        authStatus.value = false
                        passInput = ""
                        Toast.makeText(context, "Clave Guardada", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = valida,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if(valida) Color.Cyan else Color.DarkGray)
            ) { Text("CONTINUAR", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    } else {
        LaunchedEffect(direccionActiva) { if (direccionActiva != "No creada") refreshAll() }
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0B0B0B)) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Rounded.AccountBalanceWallet, null) },
                        label = { Text("Billetera", fontSize = 10.sp) },
                        selected = tabSeleccionada == 0,
                        onClick = { tabSeleccionada = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Rounded.CurrencyExchange, null) },
                        label = { Text("Conversión", fontSize = 10.sp) },
                        selected = tabSeleccionada == 1,
                        onClick = { tabSeleccionada = 1 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Rounded.Chat, null) },
                        label = { Text("LutiChat", fontSize = 10.sp) },
                        selected = tabSeleccionada == 2,
                        onClick = { tabSeleccionada = 2 }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (tabSeleccionada) {
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Column(Modifier.clickable { if(listaFrases.isNotEmpty()) menuExpandido = true }) {
                                    Text("LUTIWALLET", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    if (listaFrases.isNotEmpty()) Text("Cuenta ${walletActualIdx + 1} ▾", fontSize = 11.sp, color = Color.Cyan)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { mostrarConfig = true }, modifier = Modifier.background(Color.White.copy(0.05f), CircleShape)) {
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
                                            Text(fiatSeleccionada, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        DropdownMenu(expanded = menuMonedaExpandido, onDismissRequest = { menuMonedaExpandido = false }) {
                                            monedasFiat.forEach { moneda ->
                                                DropdownMenuItem(text = { Text(moneda) }, onClick = {
                                                    fiatSeleccionada = moneda
                                                    prefsSeguras.edit().putString("fiat_pref", moneda).apply()
                                                    menuMonedaExpandido = false
                                                })
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { refreshAll() }, modifier = Modifier.background(Color.White.copy(0.05f), CircleShape)) {
                                        if (cargando) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Cyan, strokeWidth = 2.dp)
                                        else Icon(Icons.Default.Refresh, null, tint = Color.White)
                                    }
                                }
                                DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                                    listaFrases.forEachIndexed { index, _ ->
                                        DropdownMenuItem(text = { Text("Cuenta ${index + 1}") }, onClick = {
                                            walletActualIdx = index
                                            mostrandoRegistro = false
                                            prefsSeguras.edit().putInt("current_idx", index).apply()
                                            prefsSeguras.edit().putStringSet("lista_frases", listaFrases.toSet()).apply()
                                            menuExpandido = false
                                        })
                                    }
                                    HorizontalDivider(color = Color.White.copy(0.1f))
                                    DropdownMenuItem(text = { Text("+ Nueva cuenta", color = Color.Cyan) }, onClick = { mostrandoRegistro = true; menuExpandido = false })
                                    DropdownMenuItem(text = { Text("Bloquear Billetera", color = Color.Red) }, onClick = {
                                        isAuthenticated = false
                                        authStatus.value = true
                                        menuExpandido = false
                                    })
                                }
                            }
                            if (!mostrandoRegistro && fraseActiva.isNotEmpty()) {
                                Box(modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF121212))), RoundedCornerShape(28.dp))
                                    .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(28.dp))
                                    .padding(28.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(t["balance"] ?: "BALANCE DISPONIBLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = if (saldoVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp).clickable {
                                                    saldoVisible = !saldoVisible
                                                    prefsSeguras.edit().putBoolean("saldo_visible", saldoVisible).apply()
                                                }
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            if (saldoVisible) "${saldo.format(4)} SOL" else "**** SOL",
                                            fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                                        )
                                        val factor = when(fiatSeleccionada) {
                                            "ARS" -> precioDolarCrypto; "BRL" -> 5.15; "PYG" -> 7400.0;
                                            "EUR" -> 0.92; "CNY" -> 7.23; "INR" -> 83.3;
                                            "AED" -> 3.67; "GBP" -> 0.79; "MXN" -> 16.5; else -> 1.0
                                        }
                                        val totalFiat = saldo * precioUsd * factor
                                        Surface(color = Color.Cyan.copy(0.1f), shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(top = 8.dp)) {
                                            Text(
                                                if (saldoVisible) "${if(fiatSeleccionada=="ARS") "$" else "u$"} ${totalFiat.format(2)} $fiatSeleccionada" else "**** $fiatSeleccionada",
                                                color = Color.Cyan,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                                    if (!mostrarOpcionesEnvio) {
                                        ActionButton(Modifier.weight(1f), t["recibir"] ?: "Recibir", Icons.Rounded.QrCode, Color.White) {
                                            mostrarDialogoRecibir = true
                                        }
                                        ActionButton(Modifier.weight(1f), t["enviar"] ?: "Enviar", Icons.Rounded.Send, Color.Cyan) {
                                            mostrarOpcionesEnvio = true
                                        }
                                    } else {
                                        ActionButton(Modifier.weight(1f), "Escanear", Icons.Rounded.PhotoCamera, Color.White) {
                                            mostrarCamara = true
                                        }
                                        ActionButton(Modifier.weight(1f), "Retirar", Icons.Rounded.ArrowUpward, Color.Cyan) {
                                            mostrarDialogoEnvio = true
                                            mostrarOpcionesEnvio = false
                                        }
                                    }
                                }
                                if (mostrarOpcionesEnvio) {
                                    TextButton(
                                        onClick = { mostrarOpcionesEnvio = false },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text("Volver", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                                Spacer(Modifier.height(32.dp))
                                Text("HISTORIAL DE ACTIVIDAD", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.5.sp)
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
                                    items(historialFiltrado) { tx ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { txSeleccionada = tx },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier.size(44.dp).background(if (tx.esEnvio) Color.White.copy(0.05f) else Color.Cyan.copy(0.1f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if(tx.esEnvio) Icons.Rounded.NorthEast else Icons.Rounded.SouthWest,
                                                    null,
                                                    tint = if(tx.esEnvio) Color.White else Color.Cyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                                Text(tx.tipo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Text("${tx.fecha} • ${tx.hora}", color = Color.Gray, fontSize = 11.sp)
                                            }
                                            Text(
                                                "${if(tx.esEnvio) "-" else "+"} ${tx.monto}",
                                                color = if(tx.esEnvio) Color.White else Color.Cyan,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                                    if (!modoImportar) {
                                        Box(Modifier.size(80.dp).background(Color.Cyan.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.Cyan, modifier = Modifier.size(40.dp))
                                        }
                                        Spacer(Modifier.height(24.dp))
                                        Button(onClick = {
                                            val entropy = ByteArray(16); SecureRandom().nextBytes(entropy)
                                            val nueva = MnemonicCode.INSTANCE.toMnemonic(entropy).joinToString(" ")
                                            listaFrases.add(nueva)
                                            val setParaGuardar = listaFrases.map { LutiCryptoManager.encrypt( it ) }.toSet()
                                            prefsSeguras.edit().putStringSet("lista_frases", setParaGuardar).apply()
                                            prefsSeguras.edit().putInt("current_idx", listaFrases.size - 1).apply()
                                            val keyS = "frase_segura_${SolanaUtils.generateAddressFromMnemonic(nueva)}"
                                            val fraseCifrada = LutiCryptoManager.encrypt(nueva)
                                            prefsSeguras.edit().putString(keyS, fraseCifrada).apply()
                                            walletActualIdx = listaFrases.size - 1
                                            fraseRecienGenerada = nueva
                                            mostrandoRegistro = false
                                            creandoPass = true
                                            passInput = ""
                                        }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) { Text("GENERAR BILLETERA", color = Color.Black, fontWeight = FontWeight.Bold) }
                                        TextButton(onClick = { modoImportar = true }) { Text("Importar con frase semilla", color = Color.Gray) }
                                    } else {
                                        var input by remember { mutableStateOf("") }
                                        Text("Importar Billetera", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(20.dp))
                                        OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("12 palabras semilla") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                                        Button(onClick = {
                                            if (input.trim().split(" ").size == 12) {
                                                val fraseImportada = input.trim()
                                                listaFrases.add(fraseImportada)
                                                val setParaGuardar = listaFrases.map { LutiCryptoManager.encrypt( it ) }.toSet()
                                                prefsSeguras.edit().putStringSet("lista_frases", setParaGuardar).apply()
                                                prefsSeguras.edit().putInt("current_idx", listaFrases.size - 1).apply()
                                                val keyS = "frase_segura_${SolanaUtils.generateAddressFromMnemonic(fraseImportada)}"
                                                val fraseCifrada = LutiCryptoManager.encrypt(fraseImportada)
                                                prefsSeguras.edit().putString(keyS, fraseCifrada).apply()
                                                walletActualIdx = listaFrases.size - 1
                                                modoImportar = false
                                                mostrandoRegistro = false
                                                creandoPass = true
                                                passInput = ""
                                            }
                                        }, Modifier.fillMaxWidth().padding(top = 20.dp).height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) { Text("IMPORTAR", color = Color.Black, fontWeight = FontWeight.Bold) }
                                        TextButton(onClick = { modoImportar = false }) { Text("Volver atrás", color = Color.Gray) }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        ConversionTab(saldo, precioUsd)
                    }
                    2 -> {
                        ChatTab(direccionPropia = direccionActiva) {
                            tabSeleccionada = 0
                        }
                    }
                }
            }
        }

        if (mostrarConfig) {
            AlertDialog(
                onDismissRequest = { mostrarConfig = false; passConfirmacion = "" },
                containerColor = Color(0xFF1A1A1A),
                title = { Text(t["titulo"] ?: "CONFIGURACIÓN", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(t["idioma"] ?: "Idioma", color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    textosIdiomas.keys.forEach { lang ->
                                        DropdownMenuItem(text = { Text(lang) }, onClick = {
                                            idiomaSeleccionado = lang
                                            prefsSeguras.edit().putString("idioma_pref", lang).apply()
                                            exp = false
                                        })
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Column {
                            Text(t["frase"] ?: "Frase Semilla", color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = passConfirmacion, onValueChange = { passConfirmacion = it },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                label = { Text(t["pass"] ?: "Password", fontSize = 11.sp) }
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (hashPassword(passConfirmacion) == passGuardada) {
                                        mostrarFraseSegura = true
                                        mostrarConfig = false
                                    } else {
                                        Toast.makeText(context, "Contraseña Incorrecta", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("VER FRASE", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        ActionButton(
                            mod = Modifier.fillMaxWidth(),
                            txt = "Política de Privacidad",
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
                    TextButton(onClick = { mostrarConfig = false; passConfirmacion = "" }) {
                        Text("CERRAR", color = Color.Gray)
                    }
                }
            )
        }

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
                            Icon(if (ojoFrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = Color.Cyan)
                        }
                        Text(
                            "IMPORTANTE: Si alguien obtiene esta frase, tendrá acceso total a tus fondos.",
                            color = Color.Red, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { mostrarFraseSegura = false; passConfirmacion = "" }, colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                        Text("ENTENDIDO", color = Color.Black)
                    }
                }
            )
        }

        if (mostrarDialogoRecibir) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoRecibir = false }, containerColor = Color(0xFF1A1A1A),
                title = { Text("RECIBIR SOL", color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
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
                            onClick = { clipboardManager.setText(AnnotatedString(direccionActiva)); Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show() },
                            color = Color.Black, shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(direccionActiva, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Icon(Icons.Default.ContentCopy, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                },
                confirmButton = { Button(onClick = { mostrarDialogoRecibir = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) { Text("LISTO", color = Color.Black) } }
            )
        }

        if (mostrarDialogoEnvio) {
            var dest by remember { mutableStateOf(destinoInput) }
            var mont by remember { mutableStateOf("") }
            var errorMsj by remember { mutableStateOf<String?>(null) }
            val montoNum = mont.toDoubleOrNull() ?: 0.0
            val comisionWallet = montoNum * COMISION_PORCENTAJE
            val gasFeeRed = 0.000005
            val totalADescontar = if (montoNum > 0) montoNum + comisionWallet + (gasFeeRed * 2) else 0.0

            AlertDialog(
                onDismissRequest = { mostrarDialogoEnvio = false; errorMsj = null },
                containerColor = Color(0xFF1A1A1A),
                title = { Text("ENVIAR SOL", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = dest,
                            onValueChange = { dest = it; errorMsj = null },
                            label = { Text("Dirección Destino") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Cyan
                            )
                        )
                        OutlinedTextField(
                            value = mont,
                            onValueChange = { mont = it; errorMsj = null },
                            label = { Text("Monto SOL") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Cyan
                            )
                        )
                        if (errorMsj != null) {
                            Text(errorMsj!!, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(0.02f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Red (Gas x2):", color = Color.Gray, fontSize = 11.sp)
                                Text("0.00001 SOL", color = Color.White, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Tarifa LutiWallet (0.1%):", color = Color.Gray, fontSize = 11.sp)
                                Text("${"%.9f".format(java.util.Locale.US, comisionWallet)} SOL", color = Color.White, fontSize = 11.sp)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(0.1f))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Total:", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("${"%.9f".format(java.util.Locale.US, totalADescontar)} SOL", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (cargando) return@Button
                            val mFinal = mont.toDoubleOrNull() ?: 0.0
                            val cFinal = mFinal * COMISION_PORCENTAJE
                            val tFinal = mFinal + cFinal + 0.00001

                            if (mFinal < 0.001) {
                                errorMsj = "Mínimo de envío: 0.001 SOL"
                                return@Button
                            }
                            if (tFinal > saldo) {
                                errorMsj = "Saldo insuficiente"
                                return@Button
                            }

                            scope.launch {
                                cargando = true
                                try {
                                    val cLimpia = "%.9f".format(java.util.Locale.US, cFinal).toDouble()
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
                                            val prefsSegurasGuardado = obtenerPrefsSeguras(context)
                                            prefsSegurasGuardado.edit().apply {
                                                putInt("current_idx", walletActualIdx)
                                                apply()
                                            }
                                            Toast.makeText(context, "¡Envío exitoso!", Toast.LENGTH_LONG).show()
                                            val now = Calendar.getInstance().time
                                            val sdfFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                            val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())
                                            val esMisma = dest.trim() == direccionActiva.trim()
                                            val nuevaTxEnvio = Transaccion(
                                                tipo = if (esMisma) "Autotransferencia" else "Enviaste",
                                                monto = tFinal.toBigDecimal().stripTrailingZeros().toPlainString(),
                                                fecha = sdfFecha.format(now),
                                                hora = sdfHora.format(now),
                                                esEnvio = true,
                                                de = direccionActiva,
                                                para = dest.trim(),
                                                estado = "Éxito",
                                                direccionOwner = direccionActiva
                                            )
                                            historialTotal.add(0, nuevaTxEnvio)
                                            guardarHistorialEnDisco(context, historialTotal)
                                            dest = ""
                                            mont = ""
                                            destinoInput = ""
                                            scope.launch {
                                                delay(2500)
                                                refreshAll()
                                            }
                                        } else {
                                            errorMsj = "Error en el envío principal."
                                        }
                                    } else {
                                        errorMsj = "La red no pudo procesar la tarifa. Reintentá."
                                    }
                                } catch (e: Exception) {
                                    errorMsj = "Error: ${e.localizedMessage}"
                                }
                                cargando = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                    ) {
                        Text("CONFIRMAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
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
                        DetailItem("Estado", tx.estado, if(tx.estado == "Éxito") Color.Green else Color.Red)
                        HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                        Text("DE:", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(
                                tx.de,
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            )
                        }
                        Text("PARA:", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(
                                tx.para,
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { txSeleccionada = null }) {
                        Text("CERRAR", color = Color.Cyan)
                    }
                }
            )
        }

        if (fraseRecienGenerada != null) {
            AlertDialog(onDismissRequest = { fraseRecienGenerada = null }, title = { Text("Guarda tu Frase") },
                text = { SelectionContainer { Text(fraseRecienGenerada!!, color = Color.Cyan) } },
                confirmButton = { Button(onClick = { fraseRecienGenerada = null }) { Text("OK") } }
            )
        }

        if (mostrarCamara) {
            Dialog(onDismissRequest = { mostrarCamara = false }) {
                Box(
                    modifier = Modifier
                        .size(350.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black)
                ) {
                    QRScannerView { resultado ->
                        destinoInput = resultado
                        mostrarCamara = false
                        mostrarDialogoEnvio = true
                    }
                    Text(
                        "Apuntá al código QR",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        fontSize = 12.sp
                    )
                }
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
fun ActionButton(mod: Modifier, txt: String, ico: androidx.compose.ui.graphics.vector.ImageVector, col: Color, onClick: () -> Unit) {
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

@Composable
fun ConversionTab(saldoSol: Double, precioSolUsd: Double) {
    var inputAmount by remember { mutableStateOf("") }
    val amount = inputAmount.toDoubleOrNull() ?: 0.0
    val listaConversiones = listOf(
        "USDT" to 1.0,
        "USD"  to 1.0,
        "ARS"  to 1480.0,
        "BRL"  to 5.15,
        "PYG"  to 7400.0,
        "EUR"  to 0.92,
        "CNY"  to 7.23,
        "INR"  to 83.3,
        "AED"  to 3.67,
        "GBP"  to 0.79,
        "MXN"  to 17.1
    )
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("CONVERSOR", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(15.dp))
        OutlinedTextField(
            value = inputAmount,
            onValueChange = { if(it.all { c -> c.isDigit() || c == '.' }) inputAmount = it },
            label = { Text("Cantidad en SOL", color = Color.Cyan) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color.Cyan)
        )
        Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(listaConversiones) { (moneda, factor) ->
                val totalUsd = amount * precioSolUsd
                val totalFinal = totalUsd * factor
                Row(
                    Modifier.fillMaxWidth().background(Color.White.copy(0.05f), RoundedCornerShape(16.dp)).padding(16.dp),
                    Arrangement.SpaceBetween
                ) {
                    Text(moneda, color = Color.Gray)
                    val formato = if(moneda == "USDT" || moneda == "USD") "%.4f" else "%.2f"
                    Text(String.format(java.util.Locale.US, formato, totalFinal), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DialogoRespaldoEmergencia(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val sharedPrefs = context.getSharedPreferences("LutiWalletPrefs", android.content.Context.MODE_PRIVATE)
    val fraseRecuperada = sharedPrefs.getString("seed_phrase", "Frase no encontrada") ?: "Frase no encontrada"

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
        title = {
            androidx.compose.material3.Text(
                "RESPALDO CRÍTICO",
                color = androidx.compose.ui.graphics.Color.Cyan,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    "Copiá estas palabras antes de actualizar. Son la única forma de recuperar tus fondos:",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 14.sp
                )

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(16.dp)
                )

                androidx.compose.material3.Surface(
                    color = androidx.compose.ui.graphics.Color.Black,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Gray)
                ) {
                    androidx.compose.material3.Text(
                        text = fraseRecuperada,
                        modifier = androidx.compose.ui.Modifier.padding(16.dp),
                        color = androidx.compose.ui.graphics.Color.Yellow,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("YA LAS COPIÉ", color = androidx.compose.ui.graphics.Color.Cyan)
            }
        }
    )
}