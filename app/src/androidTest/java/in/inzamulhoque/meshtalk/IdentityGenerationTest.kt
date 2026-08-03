package `in`.inzamulhoque.meshtalk

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.inzamulhoque.meshtalk.crypto.CryptoManager
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityGenerationTest {

    @Test
    fun testIdentityGeneration() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val cryptoManager = CryptoManager(appContext)
        val identityManager = IdentityManager(cryptoManager)

        val id1 = identityManager.getMyId()
        assertNotNull(id1)
        assertTrue(id1.isNotEmpty())

        // Re-initialize and check if ID is the same (persistence)
        val cryptoManager2 = CryptoManager(appContext)
        val identityManager2 = IdentityManager(cryptoManager2)
        val id2 = identityManager2.getMyId()
        
        assertEquals("Identity should be persistent", id1, id2)
    }

    @Test
    fun testEncryptionDecryption() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val cryptoManager = CryptoManager(appContext)
        val identityManager = IdentityManager(cryptoManager)

        val message = "Top Secret Mesh Message"
        val myEncryptionKey = identityManager.getMyEncryptionKey()
        
        // Encrypt for myself
        val encrypted = identityManager.encryptMessage(message, myEncryptionKey)
        assertNotNull(encrypted)
        assertNotEquals(message, encrypted)

        // Decrypt
        val decrypted = identityManager.decryptMessage(encrypted)
        assertEquals(message, decrypted)
    }
}
