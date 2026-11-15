package it.sapienza.androidratio.appratio

import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import it.sapienza.appinterpreter.activity.AskAiActivity
import it.sapienza.appinterpreter.ai.AskAiViewModel
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.TextHandover
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "Ask your AI app" screen: the token estimate follows the choices, and Share and Copy hand over the prompt. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AskAiScreenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<AskAiActivity>? = null

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(intent: Intent) {
        scenario = ActivityScenario.launch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun currentApp() = Intent(context, AskAiActivity::class.java)
        .putExtra(AskAiViewModel.EXTRA_SUBJECT, AskAiViewModel.SUBJECT_CURRENT_APP)

    private fun tokens(): Int {
        val text = composeRule.onNodeWithTag("ai-token-estimate").text()
        return Regex("""About ([\d,]+) tokens""").find(text)!!.groupValues[1].replace(",", "").toInt()
    }

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun startedIntent(): Intent {
        var started: Intent? = null
        scenario!!.onActivity { started = shadowOf(it).nextStartedActivity }
        return requireNotNull(started) { "Nothing was started" }
    }

    private fun isFinishing(): Boolean {
        var finishing = false
        scenario!!.onActivity { finishing = it.isFinishing }
        return finishing
    }

    @Test
    fun aNewAppShowsTheEstimateAndNoCurrentAppChoice() {
        launch(Intent(context, AskAiActivity::class.java))
        composeRule.onNodeWithText("Ask your AI app").assertIsDisplayed()
        composeRule.onNodeWithTag("ai-token-estimate").performScrollTo().assertIsDisplayed()
        assertTrue("${tokens()}", tokens() in 4_000..12_000)
        composeRule.onNodeWithText("Change the current app").assertDoesNotExist()
        composeRule.onNodeWithText("Describe your app").assertExists()
    }

    @Test
    fun changingTheCurrentAppAndTypingRaiseTheEstimate() {
        launch(currentApp())
        val instructions = tokens()
        composeRule.onNodeWithTag("ai-change-current").performScrollTo().assertIsOff().performClick()
        val withApp = tokens()
        assertTrue("the current app adds to $instructions: $withApp", withApp > instructions)
        composeRule.onNodeWithText("What to change").assertExists()

        composeRule.onNodeWithTag(
            "ai-request"
        ).performScrollTo().performTextInput("Add a screen with the opening hours. ".repeat(20))
        val withRequest = tokens()
        assertTrue("740 characters add about 200: ${withRequest - withApp}", withRequest - withApp in 100..300)
    }

    @Test
    fun shortInstructionsWithLinksAreMuchSmallerLinkToThisBuildAndAreRemembered() {
        launch(Intent(context, AskAiActivity::class.java))
        val all = tokens()
        composeRule.onNodeWithTag("ai-instructions-links").performScrollTo().performClick()
        val short = tokens()
        assertTrue("$short of $all", short * 2 < all)
        composeRule.onNodeWithText("Share to AI app…").performClick()
        composeRule.waitForIdle()
        val send = IntentCompat.getParcelableExtra(startedIntent(), Intent.EXTRA_INTENT, Intent::class.java)!!
        val prompt = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(
            prompt.contains(
                "https://raw.githubusercontent.com/wakirox/JsonAppCreator/${BuildConfig.AI_DOCS_REF}/docs/AI_CONTEXT.md"
            )
        )
        scenario?.close()

        launch(Intent(context, AskAiActivity::class.java))
        assertEquals("the choice is kept for next time", short, tokens())
        composeRule.onNodeWithTag("ai-instructions-all").performScrollTo().performClick()
        assertEquals(all, tokens())
    }

    @Test
    fun thePreviewShowsExactlyWhatIsSentAndCanSendIt() {
        launch(Intent(context, AskAiActivity::class.java))
        composeRule.onNodeWithTag("ai-request").performScrollTo().performTextInput("A list of my favourite books")
        composeRule.onNodeWithTag("ai-preview").performScrollTo().performClick()
        composeRule.onNodeWithText("What's sent").assertIsDisplayed()
        // Formatted, the Markdown marks are gone; as sent, they are there.
        composeRule.onNodeWithTag(
            "ai-preview-text"
        ).performScrollToNode(hasText("A list of my favourite books", substring = true))
        composeRule.onNodeWithText("A list of my favourite books", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Your task").assertIsDisplayed()
        composeRule.onNodeWithTag("ai-preview-text").performScrollToIndex(0)
        composeRule.onNodeWithTag("ai-preview-raw").performClick()
        composeRule.onNodeWithTag("ai-preview-text").performScrollToNode(hasText("## Your task"))
        composeRule.onNodeWithText("## Your task").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Ask your AI app").assertIsDisplayed()

        composeRule.onNodeWithTag("ai-preview").performScrollTo().performClick()
        composeRule.onNodeWithText("Share to AI app…").performClick()
        composeRule.waitForIdle()
        val send = IntentCompat.getParcelableExtra(startedIntent(), Intent.EXTRA_INTENT, Intent::class.java)!!
        val prompt = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(prompt.contains("Create an Appkin v3 configuration for this app: A list of my favourite books"))
        assertTrue(isFinishing())
    }

    @Test
    fun shareStartsAChooserWithThePromptAndCloses() {
        launch(currentApp())
        composeRule.onNodeWithTag("ai-request").performScrollTo().performTextInput("A list of my favourite books")
        composeRule.onNodeWithText("Share to AI app…").performClick()
        composeRule.waitForIdle()

        val chooser = startedIntent()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        val prompt = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(prompt.contains("A list of my favourite books"))
        // Unticked, the running app isn't in the prompt.
        assertTrue(!prompt.contains(DomainController.jsonFile(context).trim()))
        assertTrue(isFinishing())
    }

    @Test
    fun copyPutsThePromptOnTheClipboardAndCloses() {
        launch(currentApp())
        composeRule.onNodeWithTag("ai-change-current").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai-request").performScrollTo().performTextInput("Add a dark theme")
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.waitForIdle()

        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!
        val copied = clip.getItemAt(0).text.toString()
        assertTrue(copied.contains("Add a dark theme"))
        assertTrue(copied.contains(DomainController.jsonFile(context).trim()))
        assertTrue(isFinishing())
    }

    @Test
    fun theEditorsUnsavedTextIsChangedByDefault() {
        val unsaved = """{ "schemaVersion": 3, "name": "Unsaved marker app", "version": "1.0", """ +
            """"mainView": { "type": "text", "title": "Hi" } }"""
        launch(
            Intent(context, AskAiActivity::class.java)
                .putExtra(AskAiViewModel.EXTRA_SUBJECT, AskAiViewModel.SUBJECT_EDITOR_TEXT)
                .putExtra(AskAiViewModel.EXTRA_TEXT_PATH, TextHandover.write(context, unsaved))
        )
        composeRule.onNodeWithTag("ai-change-current").assertIsOn()
        composeRule.onNodeWithText("Share to AI app…").performClick()
        composeRule.waitForIdle()

        val send = IntentCompat.getParcelableExtra(startedIntent(), Intent.EXTRA_INTENT, Intent::class.java)!!
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("Unsaved marker app"))
    }
}
