package it.sapienza.androidratio.appratio

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import it.sapienza.appinterpreter.activity.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** About tells reviewers and users the terms, and how to report an app or a problem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<HomeActivity>()

    private fun openAbout() {
        composeRule.onNodeWithContentDescription("Open application menu").performClick()
        composeRule.onNodeWithText("About Appkin").performClick()
    }

    @Test
    fun theTermsOfUseOpenFromAbout() {
        openAbout()
        composeRule.onNodeWithText("Terms of use").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "You are responsible for the apps you open and share",
            substring = true
        ).assertExists()
    }

    @Test
    fun reportingAProblemWritesToTheContactAddressWithTheVersions() {
        openAbout()
        composeRule.onNodeWithText("Report a problem").performScrollTo().performClick()

        val mail = shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, mail.action)
        assertEquals("mailto:info@wakirox.app", mail.dataString)
        val subject = mail.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        assertTrue(subject, subject.startsWith("Appkin ${BuildConfig.VERSION_NAME} on Android "))
    }
}
