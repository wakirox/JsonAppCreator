package it.sapienza.appinterpreter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import it.sapienza.androidratio.appratio.R

@Composable
fun ApiKeyEntryDialog(
    names: Set<String>,
    savedNames: Set<String>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val values = remember(names) {
        mutableStateMapOf<String, String>().apply { names.forEach { put(it, "") } }
    }
    val canConfirm = names.all { it in savedNames || values[it].orEmpty().isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.api_keys_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.api_keys_text))
                names.forEach { name ->
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                shapes = ButtonDefaults.shapes(),
                enabled = canConfirm,
                onClick = { onConfirm(values.filterValues { it.isNotEmpty() }) }
            ) { Text(stringResource(R.string.api_keys_load)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss, modifier = Modifier.padding(end = 4.dp)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
