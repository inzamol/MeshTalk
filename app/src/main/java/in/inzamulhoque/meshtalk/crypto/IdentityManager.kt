package `in`.inzamulhoque.meshtalk.crypto

import android.bluetooth.BluetoothAdapter
import android.util.Base64
import `in`.inzamulhoque.meshtalk.util.SettingsManager

class IdentityManager(
    private val cryptoManager: CryptoManager,
    private val settingsManager: SettingsManager
) {
    
    fun getMyId(): String {
        return Base64.encodeToString(cryptoManager.getPublicKey(), Base64.NO_WRAP)
    }

    fun getDisplayName(): String {
        settingsManager.displayName?.let { return it }
        return try {
            val name = BluetoothAdapter.getDefaultAdapter()?.name
            if (name.isNullOrBlank()) "Mesh Device" else name
        } catch (e: SecurityException) {
            "Restricted Device"
        } catch (e: Exception) {
            "Unknown Device"
        }
    }

    fun getMyEncryptionKey(): String {
        return Base64.encodeToString(cryptoManager.getEncryptionPublicKey(), Base64.NO_WRAP)
    }

    fun signMessage(message: String): String {
        val signature = cryptoManager.sign(message.toByteArray())
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun encryptMessage(message: String, remotePublicKeyB64: String): String {
        val remotePublicKey = Base64.decode(remotePublicKeyB64, Base64.NO_WRAP)
        val handle = com.google.crypto.tink.CleartextKeysetHandle.read(
            com.google.crypto.tink.BinaryKeysetReader.withBytes(remotePublicKey)
        )
        val encrypted = cryptoManager.encrypt(message.toByteArray(), handle)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptMessage(encryptedMessageB64: String): String {
        val encrypted = Base64.decode(encryptedMessageB64, Base64.NO_WRAP)
        val decrypted = cryptoManager.decrypt(encrypted)
        return String(decrypted)
    }
}
