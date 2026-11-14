package it.sapienza.androidratio.appratio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import it.sapienza.appinterpreter.activity.EditorActivity
import it.sapienza.appinterpreter.activity.HomeActivity
import it.sapienza.appinterpreter.activity.MainActivity
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.PrivateStorage
import it.sapienza.appinterpreter.utils.TextHandover
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentBoundaryAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun appLaunchesAndBlocksExternalIntentExtraAndFileSpoofing() {
        val mainInfo = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertFalse(mainInfo.exported)

        val internalSecret = File(context.filesDir, "internal-secret.json").apply {
            parentFile?.mkdirs()
            writeText("""{"secret":"PRIVATE_STORAGE_MARKER"}""")
        }
        assertTrue(PrivateStorage.isPrivateFileUri(context, Uri.fromFile(internalSecret)))
        assertNull(TextHandover.read(context, internalSecret.absolutePath))

        val handoverToken = TextHandover.write(
            context,
            """{"schemaVersion":3,"name":"INTERNAL_HANDOVER_MARKER","version":"1.0",""" +
                """"mainView":{"type":"text","label":"Private"}}"""
        )

        val spoofedShare = Intent(context, EditorActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra("incoming_file", handoverToken)
            .putExtra(
                Intent.EXTRA_TEXT,
                """{"schemaVersion":3,"name":"External Shared App","version":"1.0",""" +
                    """"mainView":{"type":"text","label":"Safe external text"}}"""
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        context.startActivity(spoofedShare)
        instrumentation.waitForIdleSync()
        assertNotNull(device.wait(Until.findObject(By.textContains("External Shared App")), 10_000))
        assertNull(device.findObject(By.textContains("INTERNAL_HANDOVER_MARKER")))

        val sampleApp = """{"schemaVersion":3,"name":"Emulator Smoke App","version":"1.0",""" +
            """"mainView":{"type":"text","label":"Rendered on live emulator"}}"""
        DomainController.setJsonFile(sampleApp, context)
        context.startActivity(
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        assertNotNull(device.wait(Until.findObject(By.textContains("Emulator Smoke App")), 10_000))
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertNotNull(device.wait(Until.findObject(By.textContains("Rendered on live emulator")), 10_000))
    }
}
