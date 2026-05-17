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
    private const val SPL_TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    private const val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJe8bv"

    suspend fun getBalance(address: String): Double = withContext(Dispatchers.IO) {
        val json = """{"jsonrpc":"2.0","id":1,"method":"getBalance","params":["$address"]}"""
        val request = Request.Builder().url(RPC_URL).post(json.toRequestBody("application/json".toMediaType())).build()
        try {
            val res = client.newCall(request).execute()
            JSONObject(res.body?.string() ?: "").getJSONObject("result").getLong("value").toDouble() / 1_000_000_000.0
        } catch (e: Exception) { 0.0 }
    }

    // Obtener token account asociada a una wallet para un mint específico
    suspend fun getTokenAccount(walletAddress: String, mintAddress: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "getTokenAccountsByOwner",
                    "params": [
                        "$walletAddress",
                        {"mint": "$mintAddress"},
                        {"encoding": "jsonParsed"}
                    ]
                }
            """.trimIndent()
            val request = Request.Builder().url(RPC_URL).post(json.toRequestBody("application/json".toMediaType())).build()
            val res = client.newCall(request).execute()
            val root = JSONObject(res.body?.string() ?: "")
            val accounts = root.getJSONObject("result").getJSONArray("value")
            if (accounts.length() > 0) {
                accounts.getJSONObject(0).getString("pubkey")
            } else null
        } catch (e: Exception) {
            Log.e("SolanaManager", "Error obteniendo token account: ${e.message}")
            null
        }
    }

    // Derivar Associated Token Account (ATA) de forma determinista
    private fun deriveATA(walletPubkey: ByteArray, mintPubkey: ByteArray): ByteArray {
        // ATA = PDA derivada de [wallet, token_program, mint]
        val splTokenProgram = Base58.base58Decode(SPL_TOKEN_PROGRAM)
        val ataProgram = Base58.base58Decode(ASSOCIATED_TOKEN_PROGRAM)

        var nonce = 255
        while (nonce >= 0) {
            val seeds = mutableListOf<ByteArray>()
            seeds.add(walletPubkey)
            seeds.add(splTokenProgram)
            seeds.add(mintPubkey)
            seeds.add(byteArrayOf(nonce.toByte()))

            try {
                val hash = createProgramAddress(seeds, ataProgram)
                if (hash != null) return hash
            } catch (e: Exception) { }
            nonce--
        }
        throw Exception("No se pudo derivar ATA")
    }

    private fun createProgramAddress(seeds: List<ByteArray>, programId: ByteArray): ByteArray? {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (seed in seeds) digest.update(seed)
        digest.update(programId)
        digest.update("ProgramDerivedAddress".toByteArray())
        val hash = digest.digest()
        // Verificar que no está en la curva ed25519 (punto válido)
        return try {
            org.bouncycastle.math.ec.rfc8032.Ed25519.validatePublicKeyPartial(hash, 0)
            null // Está en la curva, no es válido como PDA
        } catch (e: Exception) {
            hash // No está en la curva, es válido
        }
    }

    suspend fun sendSplToken(
        frase: String,
        mintAddress: String,
        destino: String,
        cantidad: Double,
        decimals: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val words = frase.trim().split("\\s+".toRegex())
            val seed = MnemonicCode.toSeed(words, "")
            val privKeyBytes = seed.copyOfRange(0, 32)
            val privKeyParams = Ed25519PrivateKeyParameters(privKeyBytes, 0)
            val senderPubkey = privKeyParams.generatePublicKey().encoded
            val senderAddress = Base58.base58Encode(senderPubkey)

            // Obtener token account del emisor
            val senderTokenAccount = getTokenAccount(senderAddress, mintAddress)
                ?: return@withContext null.also { Log.e("SolanaManager", "No tiene token account para $mintAddress") }

            // Obtener o derivar token account del receptor
            val receiverPubkey = Base58.base58Decode(destino)
            val mintPubkey = Base58.base58Decode(mintAddress)

            var receiverTokenAccount = getTokenAccount(destino, mintAddress)
            val crearATA = receiverTokenAccount == null

            if (crearATA) {
                // Derivar ATA del receptor
                val ataBytes = deriveATA(receiverPubkey, mintPubkey)
                receiverTokenAccount = Base58.base58Encode(ataBytes)
                Log.d("SolanaManager", "ATA del receptor no existe, se creará: $receiverTokenAccount")
            }

            val blockhashStr = getRecentBlockhash() ?: return@withContext null
            val blockhash = Base58.base58Decode(blockhashStr)

            val amount = (cantidad * Math.pow(10.0, decimals.toDouble())).toLong()

            val senderTokenAccountBytes = Base58.base58Decode(senderTokenAccount)
            val receiverTokenAccountBytes = Base58.base58Decode(receiverTokenAccount!!)
            val splTokenProgramBytes = Base58.base58Decode(SPL_TOKEN_PROGRAM)
            val sysProgBytes = Base58.base58Decode("11111111111111111111111111111111")
            val ataProgramBytes = Base58.base58Decode(ASSOCIATED_TOKEN_PROGRAM)
            val rentSysvarBytes = Base58.base58Decode("SysvarRent111111111111111111111111111111111")

            val message = if (crearATA) {
                // Transacción con 2 instrucciones: crear ATA + transferir
                buildSplTransferWithATA(
                    senderPubkey, receiverPubkey, mintPubkey,
                    senderTokenAccountBytes, receiverTokenAccountBytes,
                    splTokenProgramBytes, sysProgBytes, ataProgramBytes, rentSysvarBytes,
                    blockhash, amount
                )
            } else {
                // Transacción simple: solo transferir
                buildSplTransfer(
                    senderPubkey, senderTokenAccountBytes, receiverTokenAccountBytes,
                    splTokenProgramBytes, blockhash, amount
                )
            }

            // Firmar
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
            Log.e("SolanaManager", "Error en sendSplToken: ${e.message}")
            null
        }
    }

    private fun buildSplTransfer(
        senderPubkey: ByteArray,
        senderTokenAccount: ByteArray,
        receiverTokenAccount: ByteArray,
        splTokenProgram: ByteArray,
        blockhash: ByteArray,
        amount: Long
    ): ByteArray {
        // Instrucción Transfer del SPL Token Program
        // [3] = instrucción Transfer, [amount 8 bytes LE]
        val instrData = ByteBuffer.allocate(9).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(3) // Transfer instruction
            putLong(amount)
        }.run {
            flip(); val out = ByteArray(remaining()); get(out); out
        }

        return ByteBuffer.allocate(512).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            // Header: numRequiredSignatures=1, numReadonlySigned=0, numReadonlyUnsigned=1
            put(1); put(0); put(1)

            // Accounts: sender_wallet, sender_token_account, receiver_token_account, spl_token_program
            put(4)
            put(senderPubkey)
            put(senderTokenAccount)
            put(receiverTokenAccount)
            put(splTokenProgram)

            // Recent blockhash
            put(blockhash)

            // Instructions: 1 instrucción
            put(1)
            // Instruction: program_id_index=3 (spl_token_program)
            put(3)
            // Accounts: [1=sender_token, 2=receiver_token, 0=sender_wallet(authority)]
            put(3); put(1); put(2); put(0)
            // Data
            put(instrData.size.toByte())
            put(instrData)

        }.run {
            val out = ByteArray(position()); flip(); get(out); out
        }
    }

    private fun buildSplTransferWithATA(
        senderPubkey: ByteArray,
        receiverPubkey: ByteArray,
        mintPubkey: ByteArray,
        senderTokenAccount: ByteArray,
        receiverTokenAccount: ByteArray,
        splTokenProgram: ByteArray,
        sysProgram: ByteArray,
        ataProgram: ByteArray,
        rentSysvar: ByteArray,
        blockhash: ByteArray,
        amount: Long
    ): ByteArray {
        val transferData = ByteBuffer.allocate(9).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(3)
            putLong(amount)
        }.run { flip(); val out = ByteArray(remaining()); get(out); out }

        return ByteBuffer.allocate(1024).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            // Header
            put(1); put(0); put(1)

            // Accounts
            put(8)
            put(senderPubkey)          // 0 - signer/payer
            put(receiverTokenAccount)   // 1 - ATA a crear
            put(receiverPubkey)         // 2 - wallet del receptor
            put(mintPubkey)             // 3 - mint
            put(sysProgram)             // 4 - system program
            put(splTokenProgram)        // 5 - spl token program
            put(ataProgram)             // 6 - associated token program
            put(senderTokenAccount)     // 7 - token account del emisor

            // Blockhash
            put(blockhash)

            // 2 instrucciones
            put(2)

            // Instrucción 1: CreateAssociatedTokenAccount
            put(6) // ataProgram index
            put(7) // accounts count
            put(0); put(1); put(2); put(3); put(4); put(5); put(6)
            put(0) // data length = 0

            // Instrucción 2: Transfer
            put(5) // splTokenProgram index
            put(3) // accounts count
            put(7); put(1); put(0) // sender_token, receiver_token, authority
            put(transferData.size.toByte())
            put(transferData)

        }.run {
            val out = ByteArray(position()); flip(); get(out); out
        }
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
                val out = ByteArray(position()); flip(); get(out); out
            }

            val signer = Ed25519Signer()
            signer.init(true, privKeyParams)
            signer.update(message, 0, message.size)
            val signature = signer.generateSignature()

            val transaction = ByteBuffer.allocate(1 + 64 + message.size).apply {
                put(1); put(signature); put(message)
            }.run {
                flip(); val out = ByteArray(remaining()); get(out); out
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
        val json = """{"jsonrpc":"2.0","id":1,"method":"sendTransaction","params":["$base64Tx", {"encoding":"base64","skipPreflight":true}]}"""
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