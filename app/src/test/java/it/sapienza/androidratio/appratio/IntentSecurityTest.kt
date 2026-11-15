package it.sapienza.androidratio.appratio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import it.sapienza.appinterpreter.activity.HomeActivity
import it.sapienza.appinterpreter.activity.MainActivity
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.HomeShortcuts
import it.sapienza.appinterpreter.utils.PrivateStorage
import it.sapienza.appinterpreter.utils.TextHandover
import it.sapienza.appinterpreter.utils.of
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

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

    @Test
    fun pruneExpiredRemovesOnlyFilesOlderThanADay() {
        val folder = File(context.cacheDir, "prune-test").apply { mkdirs() }
        val now = 100L * 24 * 60 * 60 * 1000
        val old = File(folder, "old.txt").apply { writeText("x") }
        val recent = File(folder, "recent.txt").apply { writeText("x") }
        old.setLastModified(now - 25L * 60 * 60 * 1000)
        recent.setLastModified(now - 2L * 60 * 60 * 1000)

        TextHandover.pruneExpired(folder, now)

        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun readingAHandoverFileKeepsItFromExpiring() {
        val path = TextHandover.write(context, "kept")
        val file = File(path).apply { setLastModified(System.currentTimeMillis() - 23L * 60 * 60 * 1000) }
        val before = file.lastModified()

        assertEquals("kept", TextHandover.read(context, path))

        assertTrue(file.lastModified() > before)
    }

    private fun shortcutApp(): AppLibrary.SavedApp {
        val json = """{"schemaVersion":3,"name":"Shortcut Launch","version":"1.0",""" +
            """"mainView":{"type":"text","label":"Hi"}}"""
        AppLibrary.of(context).save(json, "file:shortcut-launch")
        return checkNotNull(AppLibrary.of(context).get(AppLibrary.idOf("Shortcut Launch")))
    }

    @Test
    fun homeShortcutOpensTheSavedAppAndFinishes() {
        val saved = shortcutApp()
        val intent = Intent(context, HomeActivity::class.java).putExtra(HomeShortcuts.EXTRA_APP_ID, saved.id)

        val activity = Robolectric.buildActivity(HomeActivity::class.java, intent).create().get()

        assertTrue(activity.isFinishing)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ComponentName(context, MainActivity::class.java), started.component)
        assertEquals(saved.json, DomainController.jsonFile(context))
    }

    @Test
    fun homeShortcutOfADeletedAppShowsTheDeletedToastAndStays() {
        val saved = shortcutApp()
        AppLibrary.of(context).delete(saved.id)
        val intent = Intent(context, HomeActivity::class.java).putExtra(HomeShortcuts.EXTRA_APP_ID, saved.id)

        val activity = Robolectric.buildActivity(HomeActivity::class.java, intent).create().get()

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(context.getString(R.string.app_deleted), ShadowToast.getTextOfLatestToast())
    }
}
