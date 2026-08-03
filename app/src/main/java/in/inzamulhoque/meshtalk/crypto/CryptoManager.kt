package `in`.inzamulhoque.meshtalk.crypto

import android.content.Context
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

        keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, keysetName, prefFileName)
            .withKeyTemplate(KeyTemplates.get("ED25519"))
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle

        encryptionKeysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, encryptionKeysetName, prefFileName)
            .withKeyTemplate(KeyTemplates.get("ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM"))
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle
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
