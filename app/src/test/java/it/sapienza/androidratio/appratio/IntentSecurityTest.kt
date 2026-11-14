package it.sapienza.androidratio.appratio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import it.sapienza.appinterpreter.activity.HomeActivity
import it.sapienza.appinterpreter.activity.MainActivity
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.HomeShortcuts
import it.sapienza.appinterpreter.utils.PrivateStorage
import it.sapienza.appinterpreter.utils.TextHandover
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntentSecurityTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun textHandoverReadsOnlyConfinedIncomingTokens() {
        val writtenPath = TextHandover.write(context, """{"hello":"world"}""")
        assertEquals("""{"hello":"world"}""", TextHandover.read(context, writtenPath))
        assertEquals("""{"hello":"world"}""", TextHandover.read(context, File(writtenPath).name))

        val secretFile = File(context.filesDir, "secret.json").apply {
            parentFile?.mkdirs()
            writeText("""{"secret":true}""")
        }
        assertNull(TextHandover.read(context, secretFile.absolutePath))
        assertNull(TextHandover.read(context, "../files/secret.json"))

        val fakeExternal = File(context.filesDir, File(writtenPath).name).apply {
            writeText("""{"spoofed":true}""")
        }
        assertNull(TextHandover.read(context, fakeExternal.absolutePath))
    }

    @Test
    fun privateStorageRejectsAppDataFilesAndAllowsExternalUris() {
        val internalFile = File(context.filesDir, "private.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }
        val cacheFile = File(context.cacheDir, "cached.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }
        val externalTemp = File.createTempFile("appkin-external-", ".json").apply {
            writeText("{}")
            deleteOnExit()
        }

        assertTrue(PrivateStorage.isPrivateFileUri(context, Uri.fromFile(internalFile)))
        assertTrue(PrivateStorage.isPrivateFileUri(context, Uri.fromFile(cacheFile)))
        assertFalse(PrivateStorage.isPrivateFileUri(context, Uri.fromFile(externalTemp)))
        assertFalse(
            PrivateStorage.isPrivateFileUri(
                context,
                Uri.parse("content://com.example.provider/documents/sample.json")
            )
        )
    }

    @Test
    fun mainActivityIsInternalAndPinnedShortcutsRouteThroughHomeActivity() {
        val mainInfo = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertFalse(mainInfo.exported)

        val saved = AppLibrary.SavedApp(
            id = "shortcut-test-app",
            name = "Shortcut Test",
            json = """{"schemaVersion":3,"name":"Shortcut Test","version":"1.0",""" +
                """"mainView":{"type":"text","label":"Hi"}}""",
            savedAt = 1L,
            configurationId = "file:shortcut-test"
        )
        assertTrue(HomeShortcuts(context).pin(saved))
        val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .first { it.id == saved.id }
        assertEquals(ComponentName(context, HomeActivity::class.java), pinned.intent.component)
        assertEquals(saved.id, pinned.intent.getStringExtra(HomeShortcuts.EXTRA_APP_ID))
    }
}
