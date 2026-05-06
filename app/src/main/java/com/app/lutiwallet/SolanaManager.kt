package com.app.lutiwallet

import android.util.Base64
import android.util.Log
import io.github.novacrypto.base58.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bitcoinj.crypto.MnemonicCode
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SolanaManager {
    private val client = OkHttpClient()
    private const val RPC_URL = "https://api.mainnet-beta.solana.com"

    suspend fun getBalance(address: String): Double = withContext(Dispatchers.IO) {
        val json = """{"jsonrpc":"2.0","id":1,"method":"getBalance","params":["$address"]}"""
        val request = Request.Builder().url(RPC_URL).post(json.toRequestBody("application/json".toMediaType())).build()
        try {
            val res = client.newCall(request).execute()
            JSONObject(res.body?.string() ?: "").getJSONObject("result").getLong("value").toDouble() / 1_000_000_000.0
        } catch (e: Exception) { 0.0 }
    }

    suspend fun sendSolReal(frase: String, destino: String, monto: Double): String? = withContext(Dispatchers.IO) {
        try {
            val blockhashStr = getRecentBlockhash() ?: return@withContext null
            val blockhash = Base58.base58Decode(blockhashStr)


            val words = frase.trim().split("\\s+".toRegex())
            val seed = MnemonicCode.toSeed(words, "")
            val privKeyBytes = seed.copyOfRange(0, 32)
            val privKeyParams = Ed25519PrivateKeyParameters(privKeyBytes, 0)
            val pubKeyBytes = privKeyParams.generatePublicKey().encoded


            val lamports = (monto * 1_000_000_000).toLong()
            val destPubkey = Base58.base58Decode(destino)
            val sysProgId = Base58.base58Decode("11111111111111111111111111111111")

            val message = ByteBuffer.allocate(256).apply {
                order(ByteOrder.LITTLE_ENDIAN)


                put(1); put(0); put(1)


                put(3)
                put(pubKeyBytes)
                put(destPubkey)
                put(sysProgId)


                put(blockhash)


                put(1)
                put(2)


                put(2)
                put(0); put(1)


                put(12)
                putInt(2)
                putLong(lamports)

            }.run {
                val out = ByteArray(position())
                flip()
                get(out)
                out
            }


            val signer = Ed25519Signer()
            signer.init(true, privKeyParams)
            signer.update(message, 0, message.size)
            val signature = signer.generateSignature()


            val transaction = ByteBuffer.allocate(1 + 64 + message.size).apply {
                put(1)
                put(signature)
                put(message)
            }.run {
                flip()
                val out = ByteArray(remaining())
                get(out)
                out
            }

            val base64Tx = Base64.encodeToString(transaction, Base64.NO_WRAP)
            broadcastTransaction(base64Tx)

        } catch (e: Exception) {
            Log.e("LutiWallet", "Error en firma: ${e.message}")
            null
        }
    }

    private suspend fun getRecentBlockhash(): String? = withContext(Dispatchers.IO) {
        val json = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"finalized"}]}"""
        val request = Request.Builder().url(RPC_URL).post(json.toRequestBody("application/json".toMediaType())).build()
        try {
            val res = client.newCall(request).execute()
            JSONObject(res.body?.string() ?: "").getJSONObject("result").getJSONObject("value").getString("blockhash")
        } catch (e: Exception) { null }
    }

    private suspend fun broadcastTransaction(base64Tx: String): String? = withContext(Dispatchers.IO) {
        val json = """{"jsonrpc":"2.0","id":1,"method":"sendTransaction","params":["$base64Tx", {"encoding":"base64"}]}"""
        val request = Request.Builder().url(RPC_URL).post(json.toRequestBody("application/json".toMediaType())).build()
        try {
            val res = client.newCall(request).execute()
            val jsonRes = JSONObject(res.body?.string() ?: "")
            if (jsonRes.has("result")) {
                jsonRes.getString("result")
            } else {
                Log.e("LutiWallet", "RPC Rechazó la TX: $jsonRes")
                null
            }
        } catch (e: Exception) { null }
    }
}