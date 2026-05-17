package com.app.lutiwallet.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val PBKDF2_PREFIX = "pbkdf2:"
private const val PBKDF2_ITERATIONS = 120_000
private const val PBKDF2_KEY_BITS = 256

fun hashPassword(password: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val hash = factory.generateSecret(spec).encoded
    spec.clearPassword()
    val saltHex = salt.joinToString("") { "%02x".format(it) }
    val hashHex = hash.joinToString("") { "%02x".format(it) }
    return "$PBKDF2_PREFIX$saltHex:$hashHex"
}

fun verificarPassword(input: String, stored: String, prefsSeguras: SharedPreferences? = null): Boolean {
    return if (stored.startsWith(PBKDF2_PREFIX)) {
        val parts = stored.removePrefix(PBKDF2_PREFIX).split(":")
        if (parts.size != 2) return false
        val salt = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val expectedHashHex = parts[1]
        val spec = PBEKeySpec(input.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val computedHash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        computedHash.joinToString("") { "%02x".format(it) } == expectedHashHex
    } else {
        val legacyHash = MessageDigest.getInstance("SHA-256")
            .digest(input.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (legacyHash == stored) {
            prefsSeguras?.edit()?.putString("app_password", hashPassword(input))?.apply()
            true
        } else {
            false
        }
    }
}

fun esPasswordValida(pass: String): Boolean {
    val tieneMayuscula = pass.any { it.isUpperCase() }
    val tieneNumero = pass.any { it.isDigit() }
    val tieneEspecial = pass.any { !it.isLetterOrDigit() }
    return pass.length >= 8 && tieneMayuscula && tieneNumero && tieneEspecial
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
        Log.e("LutiWallet", "CRITICO: No se pudieron abrir las preferencias cifradas. " +
            "El archivo NO fue borrado — los fondos siguen en la blockchain.", e)
        throw RuntimeException("Error al acceder a las preferencias cifradas de la wallet.", e)
    }
}

fun mostrarBiometria(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: () -> Unit = {}
) {
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK

    val canAuth = BiometricManager.from(activity).canAuthenticate(authenticators)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        Log.w("LutiBiometria", "Biometría no disponible (código $canAuth)")
        onError()
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError()
                }
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear LutiWallet")
        .setSubtitle("Usá tu biometría para entrar")
        .setAllowedAuthenticators(authenticators)
        .setNegativeButtonText("Usar contraseña")
        .build()
    biometricPrompt.authenticate(promptInfo)
}
