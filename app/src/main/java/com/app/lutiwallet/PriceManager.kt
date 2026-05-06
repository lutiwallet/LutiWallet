package com.app.lutiwallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object PriceManager {
    private val client = OkHttpClient()


    suspend fun getSolPriceInUsd(): Double = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd")
            .build()
        try {
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            json.getJSONObject("solana").getDouble("usd")
        } catch (e: Exception) { 185.0 }
    }


    suspend fun getDolarCryptoArs(): Double = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://criptoya.com/api/dolar")
            .build()
        try {
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")

            json.getDouble("cripto")
        } catch (e: Exception) { 1200.0 }
    }
}