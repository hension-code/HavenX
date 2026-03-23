package sh.haven.feature.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sh.haven.core.data.db.entities.ConnectionProfile

@Composable
fun PasswordDialog(
    profile: ConnectionProfile,
    hasKeys: Boolean,
    onDismiss: () -> Unit,
    onConnect: (password: String, rememberPassword: Boolean) -> Unit,
    zh: Boolean = Locale.current.language == "zh",
) {
    var password by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(!profile.sshPassword.isNullOrBlank()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_to_profile_label)) },
        text = {
            Column {
                when {
                    profile.isRdp -> {
                        Text("${profile.rdpUsername ?: profile.username}@${profile.host}:${profile.rdpPort}")
                        if (!profile.rdpDomain.isNullOrBlank()) {
                            Text(
                                "${stringResource(R.string.domain)}: ${profile.rdpDomain}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> Text("${profile.username}@${profile.host}:${profile.port}")
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { onConnect(password, rememberPassword) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (profile.isSsh || profile.isSmb) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = rememberPassword,
                            onCheckedChange = { rememberPassword = it },
                        )
                        Text(
                            stringResource(R.string.remember_password),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (hasKeys && profile.isSsh) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.leave_empty_to_connect_with),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(password, rememberPassword) }) {
                Text(stringResource(R.string.connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
