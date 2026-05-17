package com.app.lutiwallet

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object TokenManager {
    private val client = OkHttpClient()


    fun guardarToken(context: Context, mintAddress: String) {
        val prefs = context.getSharedPreferences("LutiTokens", Context.MODE_PRIVATE)
        val tokens = obtenerTokensGuardados(context).toMutableSet()
        tokens.add(mintAddress)
        prefs.edit().putStringSet("lista_mints", tokens).apply()
    }

    fun obtenerTokensGuardados(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("LutiTokens", Context.MODE_PRIVATE)
        return prefs.getStringSet("lista_mints", emptySet()) ?: emptySet()
    }

    suspend fun getTokenPrice(mintAddress: String): Double = withContext(Dispatchers.IO) {
        try {

            val id = if (mintAddress == "SOL") "So11111111111111111111111111111111111111112" else mintAddress
            val url = "https://api.jup.ag/price/v2?ids=$id"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            json.getJSONObject("data").getJSONObject(id).getString("price").toDouble()
        } catch (e: Exception) {
            Log.e("TokenManager", "Error obteniendo precio de $mintAddress: ${e.message}")
            0.0
        }
    }


    suspend fun getTokenMetadata(mintAddress: String): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {

            val url = "https://api.dexscreener.com/latest/dex/tokens/$mintAddress"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            val pair = json.getJSONArray("pairs").getJSONObject(0).getJSONObject("baseToken")

            val nombre = pair.getString("name")
            val simbolo = pair.getString("symbol")
            val logoUrl = "" // Algunos providers dan el icon aquí, si no, se busca por CDN

            Triple(nombre, simbolo, logoUrl)
        } catch (e: Exception) {
            Triple("Token Desconocido", "???", "")
        }
    }


    suspend fun getSPLBalance(userAddress: String, mintAddress: String): Double {

        return 0.0
    }
}