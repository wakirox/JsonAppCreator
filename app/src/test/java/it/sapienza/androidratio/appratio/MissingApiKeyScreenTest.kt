package it.sapienza.androidratio.appratio

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import it.sapienza.appinterpreter.activity.MainActivity
import it.sapienza.appinterpreter.security.ConfigurationApiKeys
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MissingApiKeyScreenTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val domainPreferences =
        targetContext.getSharedPreferences(DOMAIN_PREFERENCES, Context.MODE_PRIVATE)
    private val encryptedPreferences =
        targetContext.getSharedPreferences(ENCRYPTED_PREFERENCES, Context.MODE_PRIVATE)

    private var originalJson: String? = null
    private var originalConfigurationId: String? = null
    private lateinit var scenario: ActivityScenario<MainActivity>

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        originalJson = domainPreferences.getString(JSON_KEY, null)
        originalConfigurationId = domainPreferences.getString(CONFIGURATION_ID_KEY, null)
        val tmdbJson = targetContext.resources.openRawResource(R.raw.v3_the_movie_db)
            .bufferedReader()
            .use { it.readText() }

        encryptedPreferences.edit().clear().commit()
        // No bundled keys either, whatever the build machine supplied (a developer's own TMDB key, say): the test is
        // about a key that's missing.
        ConfigurationApiKeys.initialize(targetContext, "")
        domainPreferences.edit()
            .putString(JSON_KEY, tmdbJson)
            .putString(CONFIGURATION_ID_KEY, TMDB_CONFIGURATION_ID)
            .commit()

        scenario = ActivityScenario.launch(
            Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @After
    fun tearDown() {
        scenario.close()
        domainPreferences.edit().apply {
            if (originalJson == null) remove(JSON_KEY) else putString(JSON_KEY, originalJson)
            if (originalConfigurationId == null) {
                remove(CONFIGURATION_ID_KEY)
            } else {
                putString(CONFIGURATION_ID_KEY, originalConfigurationId)
            }
        }.commit()
        encryptedPreferences.edit().clear().commit()
        ConfigurationApiKeys.initialize(targetContext, BuildConfig.CONFIG_API_KEYS_BASE64)
    }

    @Test
    fun aMissingKeyShowsTheKeyScreenInPlaceOfTheApp() {
        composeRule.onNodeWithText("Add your API key").assertIsDisplayed()
        composeRule.onNodeWithText("TMDB_API_KEY").assertIsDisplayed()
        composeRule.onNodeWithText("Save and open").assertIsNotEnabled()
        composeRule.onNodeWithText("Popular movies", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun typingTheKeyEnablesSaving() {
        // Saving itself needs the Android Keystore, which Robolectric lacks: it's checked on an emulator.
        composeRule.onNodeWithText("TMDB_API_KEY").performTextInput("   ")
        composeRule.onNodeWithText("Save and open").assertIsNotEnabled()
        composeRule.onNodeWithText("TMDB_API_KEY").performTextInput("my-key")
        composeRule.onNodeWithText("Save and open").assertIsEnabled()
    }

    private companion object {
        const val DOMAIN_PREFERENCES = "DomainController"
        const val ENCRYPTED_PREFERENCES = "encrypted_configuration_api_keys"
        const val JSON_KEY = "main_file_sav"
        const val CONFIGURATION_ID_KEY = "configuration_id"
        const val TMDB_CONFIGURATION_ID = "bundled:v3_the_movie_db"
    }
}
