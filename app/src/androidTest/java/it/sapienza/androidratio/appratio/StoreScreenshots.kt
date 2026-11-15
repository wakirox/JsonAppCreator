package it.sapienza.androidratio.appratio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import it.sapienza.appinterpreter.activity.EditorActivity
import it.sapienza.appinterpreter.activity.HomeActivity
import it.sapienza.appinterpreter.activity.MainActivity
import it.sapienza.appinterpreter.storage.AppData
import it.sapienza.appinterpreter.storage.Files
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.PhotoStore
import it.sapienza.appinterpreter.utils.SharedPreferencesStore
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The raw captures for the Play Store screenshots: drives the real app through each screen and saves them under the
 * app's external files dir, in store/. Run by tools/store/screenshots.sh on a clean install, never in CI.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val out = File(context.getExternalFilesDir(null), "store")

    @Before
    fun setUp() {
        assumeTrue(
            "Only when screenshots.sh asks for it",
            InstrumentationRegistry.getArguments().getString("store") == "true"
        )
        out.mkdirs()
    }

    @Test
    fun capture() {
        // Your apps: one the user made with an AI app and one changed from an example. How it works closed.
        val bookClub = instrumentation.context.assets.open("store/book_club.json").bufferedReader().use {
            it.readText()
        }

        // Enough of the user's own apps that the examples (Movies credits TMDB) stay below the screen's edge.
        fun asset(name: String) =
            instrumentation.context.assets.open("store/$name").bufferedReader().use { it.readText() }
        DomainController.setJsonFile(asset("reading_list.json"), context)
        DomainController.setJsonFile(asset("packing_list.json"), context)
        DomainController.setJsonFile(read(R.raw.v3_ai_checklist), context)
        DomainController.setJsonFile(read(R.raw.v3_race_signup), context)
        DomainController.setJsonFile(bookClub, context)
        context.getSharedPreferences(
            "Home",
            Context.MODE_PRIVATE
        ).edit().putBoolean("how_it_works_open", false).commit()
        start(HomeActivity::class.java)
        waitFor("Your apps")
        shoot("home")

        device.findObject(By.text("Ask your AI app")).click()
        waitFor("Describe your app")
        device.findObject(By.clazz("android.widget.EditText")).text = "A sign-up form for a 10 km run in Rome"
        hideKeyboard()
        shoot("ai")

        DomainController.setJsonFile(R.raw.v3_masterpieces, context)
        start(MainActivity::class.java)
        waitFor("Mona Lisa")
        settle(5000) // the paintings load from the web
        shoot("gallery")

        device.findObject(By.text("Mona Lisa")).click()
        waitFor("Where to see it")
        settle(3000)
        shoot("detail")

        // App ideas, as someone keeping notes on their own app: a bug, priorities, tags, mockups and a file.
        seedIdeas()
        DomainController.setJsonFile(R.raw.v3_app_ideas, context)
        start(MainActivity::class.java)
        waitFor("Rainy")
        device.findObject(By.text("Rainy")).click()
        waitFor("Rain alerts on the watch")
        settle(2000)
        shoot("ideas")

        DomainController.setJsonFile(R.raw.v3_race_signup, context)
        start(MainActivity::class.java)
        waitFor("Sign me up")
        val fields = device.findObjects(By.clazz("android.widget.EditText"))
        fields[0].text = "Giulia Rossi"
        fields[1].text = "giulia@example.com"
        hideKeyboard()
        device.findObject(By.text("10 km")).click()
        device.findObject(By.text("M")).click()
        // the switch, after the chips; its label doesn't toggle it
        device.findObjects(By.checkable(true)).last().click()
        settle(800)
        shoot("form")

        // Both saved apps on the phone's home screen, each through the launcher's Add to home screen sheet.
        start(HomeActivity::class.java)
        waitFor("Your apps")
        repeat(2) { index ->
            device.findObjects(By.desc("Add to home screen"))[index].click()
            waitFor("Add to home screen", exact = true)
            device.findObject(By.text("Add to home screen")).click()
            device.wait(Until.gone(By.text("Add to home screen")), 5_000)
        }
        device.pressHome()
        settle(2000)
        shoot("shortcut", clearStatusBar = false)

        EditorActivity.edit(context.applicationContext.let { ContextWrapperWithNewTask(it) })
        waitFor("Edit this app")
        settle(1500)
        shoot("editor")
    }

    /** Saves the App ideas example's data: one app with three ideas, its two mockups kept as the app keeps photos. */
    private fun seedIdeas() {
        val photos = PhotoStore(context)
        listOf("mockup-dark", "mockup-amoled").forEach { name ->
            val bitmap = instrumentation.context.assets.open("store/$name.png").use(BitmapFactory::decodeStream)
            checkNotNull(photos.file("appkin-photo:$name.jpg")).apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
        }
        val data = AppData(SharedPreferencesStore(context, "AppData"))
        val app = AppLibrary.idOf("App ideas")
        fun item(vararg fields: Pair<String, JsonElement>) = JsonObject(fields.toMap())
        fun text(value: String) = JsonPrimitive(value)
        fun list(vararg values: String) = JsonArray(values.map(::JsonPrimitive))
        val rainy = data.add(
            app,
            "apps",
            item(
                "name" to text("Rainy"),
                "description" to text("Rain alerts and forecasts, on the phone and the watch")
            )
        )
        val rainyId = text(checkNotNull((rainy["id"] as? JsonPrimitive)?.content))
        data.add(
            app,
            "ideas",
            item(
                "appId" to rainyId, "title" to text("Rain alerts on the watch"), "priority" to text("High"),
                "done" to JsonPrimitive(false),
                "description" to text("A tap on the wrist before it rains\nOnly when I'm outside"),
                "tags" to list("watch", "alerts"),
                "photos" to list("appkin-photo:mockup-dark.jpg", "appkin-photo:mockup-amoled.jpg"),
                "files" to
                    JsonArray(
                        listOf(
                            Files.Attached(
                                "watch-face-spec.pdf",
                                "application/pdf",
                                245_000,
                                "content://store/spec"
                            ).toJson()
                        )
                    ),
                "link" to text("https://wakirox.app")
            )
        )
        data.add(
            app,
            "ideas",
            item(
                "appId" to rainyId,
                "title" to text("The widget is blank after a resize"),
                "priority" to text("Medium"),
                "done" to JsonPrimitive(false),
                "bug" to text("Bug"),
                "tags" to list("widgets")
            )
        )
        data.add(
            app,
            "ideas",
            item(
                "appId" to rainyId,
                "title" to text("Dark mode"),
                "priority" to text("Low"),
                "done" to JsonPrimitive(true),
                "tags" to list("ui")
            )
        )
    }

    private fun read(resource: Int) = context.resources.openRawResource(resource).bufferedReader().use { it.readText() }

    private fun start(activity: Class<*>) {
        context.startActivity(
            Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.waitForIdle()
    }

    /** Waits for [text] anywhere on screen; a failure leaves the screen in store/failed.png. */
    private fun waitFor(text: String, exact: Boolean = false) {
        if (!device.wait(Until.hasObject(if (exact) By.text(text) else By.textContains(text)), 15_000)) {
            shoot("failed")
            error("\"$text\" didn't appear")
        }
    }

    private fun settle(millis: Long) {
        device.waitForIdle()
        SystemClock.sleep(millis)
    }

    private fun hideKeyboard() {
        if (device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")) device.pressBack()
        settle(500)
    }

    /**
     * Saves the screen, with the status bar painted over in the colour just below it when [clearStatusBar]: some
     * emulator renderers draw the status bar clipped, and the app's shots don't need its icons. `-e statusBar real`
     * keeps the real one everywhere (wakirox.app shows it, with the emulator in demo mode).
     */
    private fun shoot(name: String, clearStatusBar: Boolean = true) {
        val screen = instrumentation.uiAutomation.takeScreenshot().copy(Bitmap.Config.ARGB_8888, true)
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val keepReal = InstrumentationRegistry.getArguments().getString("statusBar") == "real"
        val bar = if (id != 0 && clearStatusBar && !keepReal) context.resources.getDimensionPixelSize(id) else 0
        val below = IntArray(screen.width).also { screen.getPixels(it, 0, screen.width, 0, bar, screen.width, 1) }
        for (y in 0 until bar) screen.setPixels(below, 0, screen.width, 0, y, screen.width, 1)
        File(out, "$name.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** EditorActivity.edit starts the activity from a plain context, which needs a new task. */
    private class ContextWrapperWithNewTask(base: Context) : android.content.ContextWrapper(base) {
        override fun startActivity(intent: Intent) = super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
