package com.app.lutiwallet

import org.bitcoinj.crypto.MnemonicCode
import org.bouncycastle.math.ec.rfc8032.Ed25519
import io.github.novacrypto.base58.Base58

object SolanaUtils {


    fun generateAddressFromMnemonic(mnemonic: String): String {
        return try {

            val words = mnemonic.trim().split("\\s+".toRegex())
            val seed = MnemonicCode.toSeed(words, "")


            val privateKey = seed.copyOfRange(0, 32)


            val publicKey = ByteArray(32)
            Ed25519.generatePublicKey(privateKey, 0, publicKey, 0)


            Base58.base58Encode(publicKey)
        } catch (e: Exception) {
            "Error al derivar"
        }
    }


    fun isValidAddress(address: String): Boolean {
        return try {
            val decoded = Base58.base58Decode(address)
            decoded.size == 32
        } catch (e: Exception) {
            false
        }
    }
}