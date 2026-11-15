package it.sapienza.androidratio.appratio

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.sapienza.appinterpreter.security.EncryptedApiKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreCleanupAndroidTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun keystoreEncryptedSecretsRoundTripAndDeletePerConfiguration() {
        val store = EncryptedApiKeyStore(context)
        val deletedConfig = "file:emulator-delete-test"
        val keptConfig = "file:emulator-keep-test"

        store.write(deletedConfig, "API_KEY", "secret-to-delete")
        store.write(keptConfig, "API_KEY", "secret-to-keep")

        assertEquals("secret-to-delete", EncryptedApiKeyStore(context).read(deletedConfig, "API_KEY"))
        assertEquals("secret-to-keep", EncryptedApiKeyStore(context).read(keptConfig, "API_KEY"))

        store.remove(deletedConfig, "API_KEY")

        assertNull(EncryptedApiKeyStore(context).read(deletedConfig, "API_KEY"))
        assertEquals("secret-to-keep", EncryptedApiKeyStore(context).read(keptConfig, "API_KEY"))

        store.remove(keptConfig, "API_KEY")
        assertNull(EncryptedApiKeyStore(context).read(keptConfig, "API_KEY"))
    }
}
