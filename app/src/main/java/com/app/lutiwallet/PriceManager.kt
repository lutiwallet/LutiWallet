package com.app.lutiwallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object PriceManager {
    private val client = OkHttpClient()


    suspend fun getSolPriceInUsd(): Double = withContext(Dispatchers.IO) {
        // Primaria: Binance — sin clave, sin rate limit estricto
        try {
            val req = Request.Builder()
                .url("https://api.binance.com/api/v3/ticker/price?symbol=SOLUSDT")
                .build()
            val body = client.newCall(req).execute().body?.string() ?: ""
            if (body.isNotEmpty()) {
                val price = JSONObject(body).getString("price").toDouble()
                if (price > 0) return@withContext price
            }
        } catch (_: Exception) {}

        // Fallback: CoinGecko
        try {
            val req = Request.Builder()
                .url("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd")
                .build()
            val body = client.newCall(req).execute().body?.string() ?: ""
            if (body.isNotEmpty()) {
                return@withContext JSONObject(body).getJSONObject("solana").getDouble("usd")
            }
        } catch (_: Exception) {}

        // Último recurso: Jupiter
        try {
            val solMint = "So11111111111111111111111111111111111111112"
            val req = Request.Builder()
                .url("https://api.jup.ag/price/v2?ids=$solMint")
                .build()
            val body = client.newCall(req).execute().body?.string() ?: ""
            if (body.isNotEmpty()) {
                val price = JSONObject(body)
                    .getJSONObject("data")
                    .getJSONObject(solMint)
                    .getDouble("price")
                if (price > 0) return@withContext price
            }
        } catch (_: Exception) {}

        185.0
    }


    suspend fun getDolarCryptoArs(): Double = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://criptoya.com/api/dolar")
            .build()
        try {
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")

            json.getDouble("cripto")
        } catch (e: Exception) { 1500.0 }
    }
}