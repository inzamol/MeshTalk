package `in`.inzamulhoque.meshtalk.crypto

import android.content.Context
import android.util.Log
import java.security.KeyStore
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException

class CryptoManager(context: Context) {

    private val masterKeyUri = "android-keystore://meshtalk_master_key"
    private val keysetName = "meshtalk_identity_keyset"
    private val encryptionKeysetName = "meshtalk_encryption_keyset"
    private val prefFileName = "meshtalk_crypto_prefs"

    private val keysetHandle: KeysetHandle
    private val encryptionKeysetHandle: KeysetHandle

    init {
        SignatureConfig.register()
        com.google.crypto.tink.hybrid.HybridConfig.register()

        var tempKeysetHandle: KeysetHandle? = null
        var tempEncryptionHandle: KeysetHandle? = null

        try {
            tempKeysetHandle = buildKeysetHandle(context, keysetName, KeyTemplates.get("ED25519"))
            tempEncryptionHandle = buildKeysetHandle(context, encryptionKeysetName, KeyTemplates.get("ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM"))
        } catch (e: Exception) {
            Log.e("CryptoManager", "Primary crypto initialization failed, attempting recovery...", e)
            try {
                resetCryptoState(context)
                tempKeysetHandle = buildKeysetHandle(context, keysetName, KeyTemplates.get("ED25519"))
                tempEncryptionHandle = buildKeysetHandle(context, encryptionKeysetName, KeyTemplates.get("ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM"))
                Log.i("CryptoManager", "Crypto recovery successful. New identity generated.")
            } catch (recoveryException: Exception) {
                Log.e("CryptoManager", "Crypto recovery failed!", recoveryException)
                throw GeneralSecurityException("Tink initialization failed permanently", recoveryException)
            }
        }

        keysetHandle = tempKeysetHandle!!
        encryptionKeysetHandle = tempEncryptionHandle!!
    }

    private fun buildKeysetHandle(context: Context, name: String, template: KeyTemplate): KeysetHandle {
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, name, prefFileName)
            .withKeyTemplate(template)
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle
    }

    private fun resetCryptoState(context: Context) {
        Log.w("CryptoManager", "Resetting crypto state: clearing prefs and deleting master key")
        
        // 1. Clear SharedPreferences
        context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE).edit().clear().apply()

        // 2. Delete Master Key from Android Keystore
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val keyAlias = masterKeyUri.removePrefix("android-keystore://")
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
                Log.i("CryptoManager", "Deleted corrupted master key: $keyAlias")
            }
        } catch (e: Exception) {
            Log.e("CryptoManager", "Failed to delete master key from Keystore", e)
        }
    }

    fun getPublicKey(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        keysetHandle.publicKeysetHandle.writeNoSecret(com.google.crypto.tink.BinaryKeysetWriter.withOutputStream(outputStream))
        return outputStream.toByteArray()
    }

    fun sign(data: ByteArray): ByteArray {
        val signer = keysetHandle.getPrimitive(PublicKeySign::class.java)
        return signer.sign(data)
    }

    fun verify(signature: ByteArray, data: ByteArray, publicKeyHandle: KeysetHandle): Boolean {
        return try {
            val verifier = publicKeyHandle.getPrimitive(PublicKeyVerify::class.java)
            verifier.verify(signature, data)
            true
        } catch (e: GeneralSecurityException) {
            false
        }
    }

    fun getEncryptionPublicKey(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        encryptionKeysetHandle.publicKeysetHandle.writeNoSecret(com.google.crypto.tink.BinaryKeysetWriter.withOutputStream(outputStream))
        return outputStream.toByteArray()
    }

    fun encrypt(data: ByteArray, publicKeysetHandle: KeysetHandle): ByteArray {
        val hybridEncrypt = publicKeysetHandle.getPrimitive(com.google.crypto.tink.HybridEncrypt::class.java)
        return hybridEncrypt.encrypt(data, null)
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        val hybridDecrypt = encryptionKeysetHandle.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
        return hybridDecrypt.decrypt(ciphertext, null)
    }
}
