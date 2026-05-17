package com.app.lutiwallet.utils

import android.util.Base64
import android.util.Log
import com.app.lutiwallet.modelo.Token
import io.github.novacrypto.base58.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bitcoinj.crypto.MnemonicCode
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object JupiterManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LutiWallet/1.1 Android")
                .build()
            chain.proceed(request)
        }
        .build()

    private const val PRICE_URL = "https://api.jup.ag/price/v2"
    private const val QUOTE_URL = "https://api.jup.ag/swap/v1/quote"
    private const val SWAP_URL  = "https://api.jup.ag/swap/v1/swap"
    private const val RPC_URL   = "https://api.mainnet-beta.solana.com"

    suspend fun obtenerPrecios(mintAddresses: List<String>): Map<String, Double> = withContext(Dispatchers.IO) {
        if (mintAddresses.isEmpty()) return@withContext emptyMap()
        val precios = mutableMapOf<String, Double>()

        val stablecoins = mapOf(
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" to 1.0,
            "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" to 1.0,
            "USDH1SM1ojwWUga67PGrgFWUHibbjqMvuMaDkRJTgkX" to 1.0,
            "7kbnvuGBxxj8AG9qp8Scn56muWGaRaFqxg1FsRp3PaFT" to 1.0
        )
        for ((mint, precio) in stablecoins) {
            if (mint in mintAddresses) precios[mint] = precio
        }

        val mintsAPedir = mintAddresses.filter { it !in precios }
        if (mintsAPedir.isEmpty()) return@withContext precios

        try {
            val ids = mintsAPedir.joinToString(",")
            val body = client.newCall(
                Request.Builder().url("$PRICE_URL?ids=$ids").get().build()
            ).execute().body?.string()
            if (!body.isNullOrEmpty()) {
                val data = JSONObject(body).optJSONObject("data")
                if (data != null) {
                    for (mint in mintsAPedir) {
                        val precio = data.optJSONObject(mint)
                            ?.optString("price", "0")?.toDoubleOrNull() ?: 0.0
                        if (precio > 0) {
                            precios[mint] = precio
                            Log.d("JupiterManager", "✓ Jupiter  $mint = ${"%.8f".format(precio)}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("JupiterManager", "Jupiter price API: ${e.message}")
        }

        val mintsSinGecko = mintsAPedir.filter { it !in precios }
        for (chunk in mintsSinGecko.chunked(50)) {
            try {
                val ids = chunk.joinToString(",")
                val body = client.newCall(
                    Request.Builder()
                        .url("https://api.coingecko.com/api/v3/simple/token_price/solana" +
                             "?contract_addresses=$ids&vs_currencies=usd")
                        .get().build()
                ).execute().body?.string()

                if (!body.isNullOrEmpty() && body != "{}") {
                    val json = JSONObject(body)
                    val mapaLower = mutableMapOf<String, Double>()
                    for (key in json.keys()) {
                        val precio = json.optJSONObject(key)?.optDouble("usd", 0.0) ?: 0.0
                        if (precio > 0) mapaLower[key.lowercase()] = precio
                    }
                    for (mint in chunk) {
                        val precio = mapaLower[mint.lowercase()] ?: continue
                        precios[mint] = precio
                        Log.d("JupiterManager", "✓ CoinGecko $mint = ${"%.8f".format(precio)}")
                    }
                }
            } catch (e: Exception) {
                Log.w("JupiterManager", "CoinGecko error: ${e.message}")
            }
        }

        val mintsSinDex = mintsAPedir.filter { it !in precios }
        for (mint in mintsSinDex) {
            try {
                val body = client.newCall(
                    Request.Builder()
                        .url("https://api.dexscreener.com/latest/dex/tokens/$mint")
                        .get().build()
                ).execute().body?.string() ?: continue
                if (body.isEmpty()) continue

                val pairs = JSONObject(body).optJSONArray("pairs") ?: continue
                var mejorPrecio = 0.0
                var mejorLiquidez = 0.0

                for (i in 0 until pairs.length()) {
                    val pair = pairs.getJSONObject(i)
                    if (pair.optString("chainId") != "solana") continue

                    val liquidez = pair.optJSONObject("liquidity")?.optDouble("usd", 0.0) ?: 0.0
                    if (liquidez < 1_000.0) continue

                    val baseAddr = pair.optJSONObject("baseToken")?.optString("address", "") ?: ""
                    if (!baseAddr.equals(mint, ignoreCase = true)) continue

                    val priceUsd = pair.optString("priceUsd", "").toDoubleOrNull() ?: 0.0
                    if (priceUsd > 0 && liquidez > mejorLiquidez) {
                        mejorLiquidez = liquidez
                        mejorPrecio = priceUsd
                    }
                }

                if (mejorPrecio > 0) {
                    precios[mint] = mejorPrecio
                    Log.d("JupiterManager", "✓ DexScreener $mint = ${"%.8f".format(mejorPrecio)} (liq: ${"%.0f".format(mejorLiquidez)})")
                }
            } catch (e: Exception) {
                Log.e("JupiterManager", "DexScreener $mint: ${e.message}")
            }
        }

        val mintsSinBinance = mintsAPedir.filter { it !in precios }
        for (mint in mintsSinBinance) {
            try {
                val jupBody = client.newCall(
                    Request.Builder().url("https://token.jup.ag/token/$mint").get().build()
                ).execute().body?.string() ?: continue
                if (jupBody.isEmpty()) continue

                val symbol = JSONObject(jupBody).optString("symbol", "").uppercase()
                if (symbol.isEmpty() || symbol.length > 12) continue

                for (quote in listOf("USDT", "BUSD")) {
                    val binBody = client.newCall(
                        Request.Builder()
                            .url("https://api.binance.com/api/v3/ticker/price?symbol=${symbol}${quote}")
                            .get().build()
                    ).execute().body?.string() ?: continue
                    val price = JSONObject(binBody).optString("price", "0").toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        precios[mint] = price
                        Log.d("JupiterManager", "✓ Binance $symbol/$quote = $price")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w("JupiterManager", "Binance $mint: ${e.message}")
            }
        }

        precios
    }

    suspend fun obtenerCambios24h(mints: List<String>): Map<String, Double> = withContext(Dispatchers.IO) {
        val cambios = mutableMapOf<String, Double>()
        if (mints.isEmpty()) return@withContext cambios

        for (chunk in mints.chunked(50)) {
            try {
                val ids = chunk.joinToString(",")
                val body = client.newCall(
                    Request.Builder()
                        .url("https://api.coingecko.com/api/v3/simple/token_price/solana" +
                             "?contract_addresses=$ids&vs_currencies=usd&include_24hr_change=true")
                        .get().build()
                ).execute().body?.string()

                if (!body.isNullOrEmpty() && body != "{}") {
                    val json = JSONObject(body)
                    val mapaLower = mutableMapOf<String, Double>()
                    for (key in json.keys()) {
                        val pct = json.optJSONObject(key)?.optDouble("usd_24h_change", Double.NaN)
                        if (pct != null && !pct.isNaN()) mapaLower[key.lowercase()] = pct
                    }
                    for (mint in chunk) {
                        val pct = mapaLower[mint.lowercase()] ?: continue
                        cambios[mint] = pct
                        Log.d("JupiterManager", "✓ Cambio24h CoinGecko $mint = ${"%.2f".format(pct)}%")
                    }
                }
            } catch (e: Exception) {
                Log.w("JupiterManager", "CoinGecko cambios: ${e.message}")
            }
        }

        val mintsSinCambio = mints.filter { it !in cambios }
        for (chunk in mintsSinCambio.chunked(30)) {
            try {
                val ids = chunk.joinToString(",")
                val body = client.newCall(
                    Request.Builder()
                        .url("https://api.dexscreener.com/latest/dex/tokens/$ids")
                        .get().build()
                ).execute().body?.string()
                if (body.isNullOrEmpty()) continue

                val pairs = JSONObject(body).optJSONArray("pairs") ?: continue
                val mejorLiquidez = mutableMapOf<String, Double>()

                for (i in 0 until pairs.length()) {
                    val pair = pairs.getJSONObject(i)
                    if (pair.optString("chainId") != "solana") continue

                    val baseAddr = pair.optJSONObject("baseToken")?.optString("address", "") ?: ""
                    val mintMatch = chunk.firstOrNull { it.equals(baseAddr, ignoreCase = true) } ?: continue

                    val liquidez = pair.optJSONObject("liquidity")?.optDouble("usd", 0.0) ?: 0.0
                    if (liquidez < 1_000.0) continue

                    val h24 = pair.optJSONObject("priceChange")?.optDouble("h24", Double.NaN) ?: Double.NaN
                    if (!h24.isNaN() && liquidez > (mejorLiquidez[mintMatch] ?: 0.0)) {
                        mejorLiquidez[mintMatch] = liquidez
                        cambios[mintMatch] = h24
                    }
                }
            } catch (e: Exception) {
                Log.w("JupiterManager", "DexScreener cambios: ${e.message}")
            }
        }

        val mintsSinBinance = mints.filter { it !in cambios }
        for (mint in mintsSinBinance) {
            try {
                val jupBody = client.newCall(
                    Request.Builder().url("https://token.jup.ag/token/$mint").get().build()
                ).execute().body?.string() ?: continue
                if (jupBody.isEmpty()) continue

                val symbol = JSONObject(jupBody).optString("symbol", "").uppercase()
                if (symbol.isEmpty() || symbol.length > 12) continue

                val binBody = client.newCall(
                    Request.Builder()
                        .url("https://api.binance.com/api/v3/ticker/24hr?symbol=${symbol}USDT")
                        .get().build()
                ).execute().body?.string() ?: continue

                val pct = JSONObject(binBody).optString("priceChangePercent", "").toDoubleOrNull()
                if (pct != null) {
                    cambios[mint] = pct
                    Log.d("JupiterManager", "✓ Cambio24h Binance $symbol = ${"%.2f".format(pct)}%")
                }
            } catch (e: Exception) {
                Log.w("JupiterManager", "Binance cambios $mint: ${e.message}")
            }
        }

        cambios
    }

    private val decimalesCache = mutableMapOf<String, Int>()

    private fun fetchDecimales(mint: String): Int {
        decimalesCache[mint]?.let { return it }

        val dec = try {
            val rpcJson = """{"jsonrpc":"2.0","id":1,"method":"getAccountInfo","params":["$mint",{"encoding":"jsonParsed"}]}"""
            val body = client.newCall(
                Request.Builder()
                    .url(RPC_URL)
                    .post(rpcJson.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().body?.string()

            val parsed = body?.let { JSONObject(it) }
                ?.optJSONObject("result")
                ?.optJSONObject("value")
                ?.optJSONObject("data")
                ?.optJSONObject("parsed")
                ?.optJSONObject("info")
                ?.optInt("decimals", -1) ?: -1

            if (parsed >= 0) parsed else null
        } catch (e: Exception) {
            Log.w("JupiterManager", "RPC decimales $mint: ${e.message}")
            null
        }
        ?: try {
            val body = client.newCall(
                Request.Builder().url("https://token.jup.ag/token/$mint").get().build()
            ).execute().body?.string()
            val v = body?.let { JSONObject(it).optInt("decimals", -1) } ?: -1
            if (v >= 0) v else null
        } catch (e: Exception) { null }
        ?: 6

        decimalesCache[mint] = dec
        Log.d("JupiterManager", "Decimales $mint = $dec")
        return dec
    }

    suspend fun buscarTokensPorNombre(query: String): List<Token> = withContext(Dispatchers.IO) {
        try {
            val esMint = query.length in 32..44 && query.none { it == ' ' }

            if (esMint) {
                val request = Request.Builder()
                    .url("https://api.dexscreener.com/latest/dex/tokens/$query")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                if (body.isEmpty()) return@withContext emptyList()

                val json = JSONObject(body)
                if (!json.has("pairs")) return@withContext emptyList()
                val pairs = json.getJSONArray("pairs")
                if (pairs.length() == 0) return@withContext emptyList()

                var mejorPar: org.json.JSONObject? = null
                var mejorLiquidez = 0.0

                for (i in 0 until pairs.length()) {
                    val pair = pairs.getJSONObject(i)
                    if (pair.optString("chainId") != "solana") continue
                    val liquidez = pair.optJSONObject("liquidity")?.optDouble("usd", 0.0) ?: 0.0
                    if (liquidez > mejorLiquidez) {
                        mejorLiquidez = liquidez
                        mejorPar = pair
                    }
                }

                if (mejorPar == null) return@withContext emptyList()

                val baseToken = mejorPar.optJSONObject("baseToken")
                val quoteToken = mejorPar.optJSONObject("quoteToken")

                val tokenInfo = if (baseToken?.optString("address", "") == query) baseToken else quoteToken
                val symbol = tokenInfo?.optString("symbol", query.take(6)) ?: query.take(6)
                val name = tokenInfo?.optString("name", symbol) ?: symbol
                val priceUsd = mejorPar.optString("priceUsd", "0").toDoubleOrNull() ?: 0.0
                val logoUrl = mejorPar.optJSONObject("info")?.optString("imageUrl", "") ?: ""
                val decimales = fetchDecimales(query)

                return@withContext listOf(
                    Token(
                        symbol = symbol,
                        name = name,
                        mintAddress = query,
                        balance = 0.0,
                        decimals = decimales,
                        precioUsd = priceUsd,
                        logoUrl = logoUrl
                    )
                )
            } else {

                val request = Request.Builder()
                    .url("https://api.dexscreener.com/latest/dex/search?q=${query.replace(" ", "%20")}")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                if (body.isEmpty()) return@withContext emptyList()

                val json = JSONObject(body)
                if (!json.has("pairs")) return@withContext emptyList()
                val pairs = json.getJSONArray("pairs")

                val resultados = mutableListOf<Token>()
                val mintsVistos = mutableSetOf<String>()

                for (i in 0 until pairs.length()) {
                    val pair = pairs.getJSONObject(i)
                    if (pair.optString("chainId") != "solana") continue

                    val baseToken = pair.optJSONObject("baseToken") ?: continue
                    val mint = baseToken.optString("address", "")
                    if (mint.isEmpty() || mint in mintsVistos) continue

                    val symbol = baseToken.optString("symbol", "")
                    val name = baseToken.optString("name", symbol)

                    if (!symbol.contains(query, ignoreCase = true) &&
                        !name.contains(query, ignoreCase = true)) continue

                    mintsVistos.add(mint)
                    val priceUsd = pair.optString("priceUsd", "0").toDoubleOrNull() ?: 0.0
                    val logoUrl = pair.optJSONObject("info")?.optString("imageUrl", "") ?: ""

                    resultados.add(
                        Token(
                            symbol = symbol,
                            name = name,
                            mintAddress = mint,
                            balance = 0.0,
                            decimals = fetchDecimales(mint),
                            precioUsd = priceUsd,
                            logoUrl = logoUrl
                        )
                    )
                    if (resultados.size >= 20) break
                }
                resultados
            }
        } catch (e: Exception) {
            Log.e("JupiterManager", "Error buscando tokens: ${e.message}")
            emptyList()
        }
    }

    suspend fun obtenerMetadataTokens(mintAddresses: List<String>): Map<String, Pair<String, String>> = withContext(Dispatchers.IO) {
        val resultado = mutableMapOf<String, Pair<String, String>>()

        for (mint in mintAddresses) {
            TOKEN_METADATA_FALLBACK[mint]?.let { resultado[mint] = it }
        }

        val mintsSinMeta = mintAddresses.filter { it !in resultado }

        for (mint in mintsSinMeta) {
            try {
                val request = Request.Builder()
                    .url("https://api.dexscreener.com/latest/dex/tokens/$mint")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                if (body.isEmpty()) continue

                val json = JSONObject(body)
                if (!json.has("pairs")) continue
                val pairs = json.getJSONArray("pairs")
                if (pairs.length() == 0) continue

                var mejorLiquidez = 0.0
                var symbol = ""
                var name = ""
                var imageUrl = ""

                for (i in 0 until pairs.length()) {
                    val pair = pairs.getJSONObject(i)
                    if (pair.optString("chainId") != "solana") continue

                    val liquidez = pair.optJSONObject("liquidity")?.optDouble("usd", 0.0) ?: 0.0
                    if (liquidez <= mejorLiquidez) continue

                    val baseToken = pair.optJSONObject("baseToken") ?: continue
                    val quoteToken = pair.optJSONObject("quoteToken")
                    val baseAddr = baseToken.optString("address", "")
                    val quoteAddr = quoteToken?.optString("address", "") ?: ""

                    val tokenInfo = when {
                        baseAddr.equals(mint, ignoreCase = true) -> baseToken
                        quoteAddr.equals(mint, ignoreCase = true) -> quoteToken
                        else -> null
                    } ?: continue

                    mejorLiquidez = liquidez
                    symbol = tokenInfo.optString("symbol", "")
                    name = tokenInfo.optString("name", symbol)
                    imageUrl = pair.optJSONObject("info")?.optString("imageUrl", "") ?: ""
                }

                if (symbol.isNotEmpty()) {
                    val logoFinal = if (imageUrl.isEmpty()) {
                        try {
                            val jupReq = Request.Builder()
                                .url("https://token.jup.ag/token/$mint")
                                .get().build()
                            val jupBody = client.newCall(jupReq).execute().body?.string()
                            if (!jupBody.isNullOrEmpty()) {
                                JSONObject(jupBody).optString("logoURI", "")
                            } else ""
                        } catch (e: Exception) { "" }
                    } else imageUrl

                    resultado[mint] = Pair(symbol, logoFinal)
                    Log.d("JupiterManager", "✓ Metadata $mint → $symbol img=$logoFinal")
                }

            } catch (e: Exception) {
                Log.e("JupiterManager", "Error metadata $mint: ${e.message}")
            }
        }

        resultado
    }

    suspend fun obtenerImagenToken(mintAddress: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.dexscreener.com/latest/dex/tokens/$mintAddress")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext ""
            if (body.isEmpty()) return@withContext ""

            val json = JSONObject(body)
            if (!json.has("pairs")) return@withContext ""
            val pairs = json.getJSONArray("pairs")

            for (i in 0 until pairs.length()) {
                val pair = pairs.getJSONObject(i)
                if (pair.optString("chainId") != "solana") continue

                val baseAddr = pair.optJSONObject("baseToken")?.optString("address", "") ?: ""
                if (!baseAddr.equals(mintAddress, ignoreCase = true)) continue

                val info = pair.optJSONObject("info") ?: continue
                val url = info.optString("imageUrl", "")
                if (url.isNotEmpty()) return@withContext url
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun obtenerCotizacion(
        mintOrigen: String,
        mintDestino: String,
        monto: Long
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = "$QUOTE_URL?inputMint=$mintOrigen&outputMint=$mintDestino&amount=$monto&slippageBps=50"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (json.has("error")) {
                Log.e("JupiterManager", "Error cotización: ${json.getString("error")}")
                return@withContext null
            }
            json
        } catch (e: Exception) {
            Log.e("JupiterManager", "Error obteniendo cotización: ${e.message}")
            null
        }
    }

    suspend fun ejecutarSwap(
        frase: String,
        direccionWallet: String,
        cotizacion: JSONObject
    ): String? = withContext(Dispatchers.IO) {
        try {
            val swapBody = JSONObject().apply {
                put("quoteResponse", cotizacion)
                put("userPublicKey", direccionWallet)
                put("wrapAndUnwrapSol", true)
                put("dynamicComputeUnitLimit", true)
                put("prioritizationFeeLamports", 1000)
            }.toString()

            val swapRequest = Request.Builder()
                .url(SWAP_URL)
                .post(swapBody.toRequestBody("application/json".toMediaType()))
                .build()

            val swapResponse = client.newCall(swapRequest).execute()
            val swapBody2 = swapResponse.body?.string() ?: return@withContext null
            val swapJson = JSONObject(swapBody2)

            if (!swapJson.has("swapTransaction")) {
                Log.e("JupiterManager", "No hay swapTransaction: $swapJson")
                return@withContext null
            }

            val txBase64 = swapJson.getString("swapTransaction")
            val txBytes = Base64.decode(txBase64, Base64.DEFAULT)

            val words = frase.trim().split("\\s+".toRegex())
            val seed = MnemonicCode.toSeed(words, "")
            val privKeyBytes = seed.copyOfRange(0, 32)
            val privKeyParams = Ed25519PrivateKeyParameters(privKeyBytes, 0)

            val numSignatures = txBytes[0].toInt() and 0xFF
            val messageOffset = 1 + (numSignatures * 64)
            val message = txBytes.copyOfRange(messageOffset, txBytes.size)

            val signer = Ed25519Signer()
            signer.init(true, privKeyParams)
            signer.update(message, 0, message.size)
            val signature = signer.generateSignature()

            val signedTx = txBytes.copyOf()
            signature.copyInto(signedTx, destinationOffset = 1)

            val signedBase64 = Base64.encodeToString(signedTx, Base64.NO_WRAP)
            broadcastSwap(signedBase64)

        } catch (e: Exception) {
            Log.e("JupiterManager", "Error ejecutando swap: ${e.message}")
            null
        }
    }

    private suspend fun broadcastSwap(base64Tx: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "sendTransaction",
                    "params": [
                        "$base64Tx",
                        {
                            "encoding": "base64",
                            "skipPreflight": true,
                            "maxRetries": 3
                        }
                    ]
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(RPC_URL)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val result = JSONObject(body)

            if (result.has("result")) {
                result.getString("result")
            } else {
                Log.e("JupiterManager", "Broadcast rechazado: $result")
                null
            }
        } catch (e: Exception) {
            Log.e("JupiterManager", "Error broadcast swap: ${e.message}")
            null
        }
    }
}
