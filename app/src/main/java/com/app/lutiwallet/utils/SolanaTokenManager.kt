package com.app.lutiwallet.utils

import android.util.Log
import com.app.lutiwallet.modelo.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SolanaTokenManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private const val RPC_URL = "https://api.mainnet-beta.solana.com"
    private const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    suspend fun obtenerTokensDeLaWallet(direccion: String): List<Token> = withContext(Dispatchers.IO) {
        try {
            val json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getTokenAccountsByOwner",
                    "params": [
                        "$direccion",
                        { "programId": "$TOKEN_PROGRAM" },
                        { "encoding": "jsonParsed" }
                    ]
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(RPC_URL)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(body)

            if (!root.has("result")) return@withContext emptyList()

            val accounts = root.getJSONObject("result").getJSONArray("value")
            val tokens = mutableListOf<Token>()

            for (i in 0 until accounts.length()) {
                try {
                    val account = accounts.getJSONObject(i)
                    val parsed = account
                        .getJSONObject("account")
                        .getJSONObject("data")
                        .getJSONObject("parsed")
                        .getJSONObject("info")

                    val mint = parsed.getString("mint")
                    val decimals = parsed.getJSONObject("tokenAmount").getInt("decimals")
                    val balanceRaw = parsed.getJSONObject("tokenAmount").getDouble("uiAmount")

                    if (balanceRaw > 0) {
                        tokens.add(
                            Token(
                                // Symbol y name temporales, Jupiter los sobreescribe
                                symbol = "???",
                                name = "Token desconocido",
                                mintAddress = mint,
                                balance = balanceRaw,
                                decimals = decimals
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SolanaTokenManager", "Error parseando token: ${e.message}")
                }
            }

            tokens
        } catch (e: Exception) {
            Log.e("SolanaTokenManager", "Error obteniendo tokens: ${e.message}")
            emptyList()
        }
    }

    suspend fun buscarTokenPorMint(mintAddress: String): Token? = withContext(Dispatchers.IO) {
        try {
            val json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getAccountInfo",
                    "params": [
                        "$mintAddress",
                        { "encoding": "jsonParsed" }
                    ]
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(RPC_URL)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val root = JSONObject(body)

            if (!root.has("result")) return@withContext null
            val result = root.getJSONObject("result")
            if (result.isNull("value")) return@withContext null

            val parsed = result
                .getJSONObject("value")
                .getJSONObject("data")
                .getJSONObject("parsed")
                .getJSONObject("info")

            val decimals = parsed.getInt("decimals")

            Token(
                symbol = "???",
                name = "Token desconocido",
                mintAddress = mintAddress,
                balance = 0.0,
                decimals = decimals
            )
        } catch (e: Exception) {
            Log.e("SolanaTokenManager", "Error buscando mint: ${e.message}")
            null
        }
    }
}