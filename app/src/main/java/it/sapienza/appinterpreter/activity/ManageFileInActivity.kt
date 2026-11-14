package it.sapienza.appinterpreter.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.ai.AiContext
import it.sapienza.appinterpreter.alerts.AlertHost
import it.sapienza.appinterpreter.alerts.AlertUtils
import it.sapienza.appinterpreter.alerts.AlertsViewModel
import it.sapienza.appinterpreter.security.ConfigurationApiKeys
import it.sapienza.appinterpreter.ui.ApiKeyEntryDialog
import it.sapienza.appinterpreter.ui.AppTheme
import it.sapienza.appinterpreter.ui.ReceivedApp
import it.sapienza.appinterpreter.ui.ReceivedAppDialog
import it.sapienza.appinterpreter.utils.AppLibrary
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.PrivateStorage
import it.sapienza.appinterpreter.utils.SharedApps
import it.sapienza.appinterpreter.utils.of
import it.sapienza.appinterpreter.validation.ConfigurationValidator
import it.sapienza.appinterpreter.validation.ValidatedConfigurationParser
import it.sapienza.appinterpreter.validation.fromAssets
import it.sapienza.appinterpreter.validation.fromSchema
import java.io.BufferedReader
import java.io.InputStreamReader

class ManageFileInActivity : AppCompatActivity() {
    private val alerts: AlertsViewModel by viewModels()

    /** A file that needs API keys, asked for before it opens. */
    private class NeedsKeys(val text: String, val names: Set<String>, val savedNames: Set<String>)
    private var needsKeys by mutableStateOf<NeedsKeys?>(null)

    /** A file that opened without problems, shown before it runs since it may come from someone else. */
    private var received by mutableStateOf<ReceivedApp?>(null)
    private val configurationParser by lazy(LazyThreadSafetyMode.NONE) {
        ValidatedConfigurationParser(ConfigurationValidator.fromAssets(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Compose)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState != null) alerts.activityRecreated()
        setContent {
            AppTheme {
                received?.let { app ->
                    ReceivedAppDialog(
                        app = app,
                        onOpen = {
                            received = null
                            open(app.json)
                        },
                        onKeepBoth = {
                            received = null
                            open(SharedApps.renamed(app.json, AppLibrary.of(this).freeName(app.name)))
                        },
                        onEdit = {
                            EditorActivity.openedFile(this, app.json)
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
                needsKeys?.let { file ->
                    ApiKeyEntryDialog(
                        names = file.names,
                        savedNames = file.savedNames,
                        onConfirm = { values ->
                            ConfigurationApiKeys.save(file.text, values)
                            commitConfiguration(file.text)
                        },
                        onDismiss = { finish() }
                    )
                }
                AlertHost(alerts)
            }
        }
        handleIntent()
    }

    private fun handleIntent() {
        try {
            val uri = intent?.data?.takeUnless { PrivateStorage.isPrivateFileUri(this, it) }
            val text = uri?.let(contentResolver::openInputStream)?.use {
                BufferedReader(InputStreamReader(it)).readText()
            } ?: throw IllegalArgumentException(getString(R.string.file_unreadable))

            val app = try {
                configurationParser.parse(text)
            } catch (problems: Exception) {
                if (AiContext.extractJson(text) != null) {
                    EditorActivity.openedFile(this, text)
                    finish()
                    return
                }
                throw problems
            }
            received = ReceivedApp(
                json = text,
                name = app.name,
                author = app.author,
                version = app.version,
                webAddresses = SharedApps.webAddresses(text),
                apiKeys = ConfigurationApiKeys.placeholderNames(text),
                arrival = SharedApps.arrival(text, AppLibrary.of(this).get(AppLibrary.idOf(app.name)))
            )
        } catch (e: Exception) {
            AlertUtils.showAlert(
                this,
                getString(R.string.file_load_failed, e.message ?: getString(R.string.error_unrecognized)),
                Runnable {
                    finish()
                }
            )
        }
    }

    /** Runs [text], after asking for the API keys it needs that aren't saved yet. */
    private fun open(text: String) {
        val names = ConfigurationApiKeys.placeholderNames(text)
        if (names.isEmpty()) {
            commitConfiguration(text)
            return
        }
        val savedNames = names.filterTo(mutableSetOf()) {
            ConfigurationApiKeys.hasSavedValue(text, it)
        }
        needsKeys = NeedsKeys(text, names, savedNames)
    }

    private fun commitConfiguration(text: String) {
        DomainController.setJsonFile(text, this)
        spawnNew(this)
        finish()
    }
}
