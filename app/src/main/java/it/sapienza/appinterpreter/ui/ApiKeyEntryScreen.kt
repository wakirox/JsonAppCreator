package it.sapienza.appinterpreter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.sapienza.androidratio.appratio.R

/**
 * Shown in place of an app that can't work without API keys this phone lacks (or has, but a service refused): one field per key, and a button that
 * saves them and opens the app. There is no way into the app without them; back leaves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyEntryScreen(
    appName: String,
    names: Set<String>,
    savedNames: Set<String>,
    /** A service refused a key: the screen says so, and every key is entered again. */
    rejected: Boolean,
    onSave: (Map<String, String>) -> Unit,
    onBack: () -> Unit
) {
    val values = remember(names) {
        mutableStateMapOf<String, String>().apply { names.forEach { put(it, "") } }
    }
    val canSave = names.all { it in savedNames || values[it].orEmpty().isNotBlank() }
    val save = { onSave(values.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (rejected) {
                    pluralStringResource(R.plurals.api_key_screen_rejected_title, names.size)
                } else {
                    pluralStringResource(R.plurals.api_key_screen_title, names.size)
                },
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                if (rejected) {
                    pluralStringResource(R.plurals.api_key_screen_rejected_text, names.size, appName)
                } else {
                    pluralStringResource(R.plurals.api_key_screen_text, names.size, appName)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            names.forEachIndexed { index, name ->
                val last = index == names.size - 1
                OutlinedTextField(
                    value = values[name].orEmpty(),
                    onValueChange = { values[name] = it },
                    label = { Text(name) },
                    supportingText = if (name in savedNames) {
                        { Text(stringResource(R.string.api_keys_keep_saved)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (last) ImeAction.Done else ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                stringResource(R.string.api_key_screen_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                shapes = ButtonDefaults.shapes(),
                enabled = canSave,
                onClick = save,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.api_key_screen_save)) }
        }
    }
}
