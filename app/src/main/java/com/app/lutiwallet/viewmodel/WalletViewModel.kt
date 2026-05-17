package com.app.lutiwallet.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.lutiwallet.modelo.Token
import com.app.lutiwallet.modelo.Transaccion
import com.app.lutiwallet.modelo.agregarTransaccionFirebase
import com.app.lutiwallet.modelo.guardarHistorialEnDisco
import com.app.lutiwallet.utils.JupiterManager
import com.app.lutiwallet.utils.NotificationHelper
import com.app.lutiwallet.utils.SolanaTokenManager
import com.app.lutiwallet.utils.format
import com.app.lutiwallet.utils.obtenerPrefsSeguras
import com.app.lutiwallet.PriceManager
import com.app.lutiwallet.SolanaManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.app.lutiwallet.utils.TOKEN_METADATA_FALLBACK

class WalletViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saldo = MutableStateFlow(0.0)
    val saldo: StateFlow<Double> = _saldo.asStateFlow()

    private val _precioUsd = MutableStateFlow(0.0)
    val precioUsd: StateFlow<Double> = _precioUsd.asStateFlow()

    private val _precioDolarCrypto = MutableStateFlow(0.0)
    val precioDolarCrypto: StateFlow<Double> = _precioDolarCrypto.asStateFlow()

    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    private val _tokens = MutableStateFlow<List<Token>>(emptyList())
    val tokens: StateFlow<List<Token>> = _tokens.asStateFlow()

    private val _totalUsd = MutableStateFlow(0.0)
    val totalUsd: StateFlow<Double> = _totalUsd.asStateFlow()

    private val _cargandoTokens = MutableStateFlow(false)
    val cargandoTokens: StateFlow<Boolean> = _cargandoTokens.asStateFlow()

    private val _updateUrl = MutableStateFlow<String?>(null)
    val updateUrl: StateFlow<String?> = _updateUrl.asStateFlow()

    private val _cambioPortfolio24h = MutableStateFlow(0.0)
    val cambioPortfolio24h: StateFlow<Double> = _cambioPortfolio24h.asStateFlow()

    private val _sinConexion = MutableStateFlow(false)
    val sinConexion: StateFlow<Boolean> = _sinConexion.asStateFlow()

    fun setUpdateUrl(url: String) { _updateUrl.value = url }


    @Volatile private var direccionActual: String = ""


    private var refreshAllJob: Job? = null


    private var refreshTokensJob: Job? = null

    init {
        viewModelScope.launch {
            delay(1500)
            _isLoading.value = false
        }
    }


    private fun hayConexion(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }


    private fun limpiarEstadoWallet() {
        _saldo.value = 0.0
        _tokens.value = emptyList()
        _totalUsd.value = 0.0
        _cambioPortfolio24h.value = 0.0
    }

    fun refreshAll(
        context: Context,
        direccionActiva: String,
        historialTotal: MutableList<Transaccion>,
        onHistorialActualizado: () -> Unit
    ) {
        if (direccionActiva == "No creada") return


        if (direccionActiva != direccionActual) {
            refreshAllJob?.cancel()
            refreshTokensJob?.cancel()
            limpiarEstadoWallet()
            direccionActual = direccionActiva
        }


        if (!hayConexion(context)) {
            _sinConexion.value = true
            _cargando.value = false
            return
        }
        _sinConexion.value = false

        refreshAllJob = viewModelScope.launch {
            _cargando.value = true
            try {
                val prefsSeguras = obtenerPrefsSeguras(context)

                val saldoEnRed = withContext(Dispatchers.IO) {
                    SolanaManager.getBalance(direccionActiva)
                }

                if (direccionActiva != direccionActual) return@launch

                val saldoAnterior = prefsSeguras
                    .getFloat("ultimo_saldo_$direccionActiva", 0f).toDouble()

                if (saldoEnRed > (saldoAnterior + 0.000001)) {
                    val diff = saldoEnRed - saldoAnterior
                    val now = Calendar.getInstance().time
                    val nuevaTx = Transaccion(
                        tipo = "Recibiste",
                        monto = diff.format(9),
                        fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now),
                        hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                        esEnvio = false,
                        de = "Remitente Externo",
                        para = direccionActiva,
                        estado = "Éxito",
                        direccionOwner = direccionActiva
                    )
                    historialTotal.add(0, nuevaTx)
                    guardarHistorialEnDisco(context, historialTotal)
                    agregarTransaccionFirebase(nuevaTx)
                    NotificationHelper.showNotification(
                        context, "Fondos Recibidos", "Has recibido ${diff.format(4)} SOL"
                    )
                    onHistorialActualizado()
                }

                _saldo.value = saldoEnRed
                prefsSeguras.edit()
                    .putFloat("ultimo_saldo_$direccionActiva", saldoEnRed.toFloat())
                    .apply()

                if (direccionActiva != direccionActual) return@launch

                _precioUsd.value = withContext(Dispatchers.IO) {
                    PriceManager.getSolPriceInUsd()
                }
                _precioDolarCrypto.value = withContext(Dispatchers.IO) {
                    PriceManager.getDolarCryptoArs()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LutiWallet", "Error de red: ${e.message}")
                Toast.makeText(
                    context,
                    "Error al actualizar saldos. Revisá tu conexión.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                _cargando.value = false
            }

            if (direccionActiva == direccionActual) {
                refreshTokensInterno(direccionActiva)
            }
        }
    }


    fun refreshTokens(direccionActiva: String) {
        if (direccionActiva == "No creada") return
        refreshTokensJob?.cancel()
        refreshTokensJob = viewModelScope.launch {
            refreshTokensInterno(direccionActiva)
        }
    }


    private suspend fun refreshTokensInterno(direccionActiva: String) {
        if (direccionActiva == "No creada") return

        val dirSnapshot = direccionActiva

        _cargandoTokens.value = true
        try {
            val tokensSpl = withContext(Dispatchers.IO) {
                SolanaTokenManager.obtenerTokensDeLaWallet(dirSnapshot)
            }


            if (dirSnapshot != direccionActual) return

            val mints = tokensSpl.map { it.mintAddress }.toMutableList()
            val solMint = "So11111111111111111111111111111111111111112"
            if (!mints.contains(solMint)) mints.add(0, solMint)


            val precios: Map<String, Double>
            val metadata: Map<String, Pair<String, String>>
            val cambios24h: Map<String, Double>
            coroutineScope {
                val preciosDeferred  = async(Dispatchers.IO) { JupiterManager.obtenerPrecios(mints) }
                val metadataDeferred = async(Dispatchers.IO) { JupiterManager.obtenerMetadataTokens(mints) }
                val cambiosDeferred  = async(Dispatchers.IO) { JupiterManager.obtenerCambios24h(mints) }
                precios    = preciosDeferred.await()
                metadata   = metadataDeferred.await()
                cambios24h = cambiosDeferred.await()
            }

            if (dirSnapshot != direccionActual) return

            Log.d("WalletViewModel", "Mints buscados: $mints")
            Log.d("WalletViewModel", "Metadata recibida: $metadata")

            val precioSol = precios[solMint] ?: _precioUsd.value
            if (precioSol > 0.0) _precioUsd.value = precioSol

            val tokenSol = Token(
                symbol = "SOL",
                name = "Solana",
                mintAddress = solMint,
                balance = _saldo.value,
                decimals = 9,
                precioUsd = precioSol,
                cambio24h = cambios24h[solMint] ?: 0.0,
                logoUrl = "https://cdn.jsdelivr.net/gh/solana-labs/token-list@main/assets/mainnet/So11111111111111111111111111111111111111112/logo.png"
            )

            val tokensConPrecio = tokensSpl.map { token ->
                val meta = metadata[token.mintAddress]
                val fallback = TOKEN_METADATA_FALLBACK[token.mintAddress]
                val mintTruncado = token.mintAddress.take(4).uppercase()
                val symbolValido = meta?.first?.takeIf {
                    it != "???" && it != mintTruncado && it.isNotEmpty()
                }
                val logoFinal = fallback?.second?.takeIf { it.isNotEmpty() }
                    ?: meta?.second?.takeIf { it.isNotEmpty() }
                    ?: "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/${token.mintAddress}/logo.png"

                token.copy(
                    symbol = fallback?.first ?: symbolValido ?: token.symbol,
                    name = fallback?.first ?: symbolValido ?: token.name,
                    precioUsd = precios[token.mintAddress] ?: 0.0,
                    cambio24h = cambios24h[token.mintAddress] ?: 0.0,
                    logoUrl = logoFinal
                )
            }

            val listaFinal = mutableListOf(tokenSol).apply { addAll(tokensConPrecio) }


            if (dirSnapshot != direccionActual) return

            _tokens.value = listaFinal

            val totalActual = listaFinal.sumOf { it.valorUsd }
            _totalUsd.value = totalActual

            val totalAyer = listaFinal.sumOf { t ->
                if (t.cambio24h != 0.0 && t.precioUsd > 0) {
                    t.balance * (t.precioUsd / (1.0 + t.cambio24h / 100.0))
                } else {
                    t.valorUsd
                }
            }
            _cambioPortfolio24h.value = if (totalAyer > 0 && totalActual > 0)
                ((totalActual - totalAyer) / totalAyer) * 100.0
            else 0.0

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error refreshTokens: ${e.message}")
        } finally {
            _cargandoTokens.value = false
        }
    }
}
