package sh.haven.feature.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import sh.haven.core.ssh.KnownHostEntry

@Composable
fun NewHostKeyDialog(
    entry: KnownHostEntry,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
    zh: Boolean = Locale.current.language == "zh",
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (zh) "验证主机密钥" else "Verify Host Key") },
        text = {
            Column {
                val hostDisplay = if (entry.port == 22) entry.hostname
                    else "${entry.hostname}:${entry.port}"
                Text(if (zh) "首次连接到 $hostDisplay。" else "Connecting to $hostDisplay for the first time.")
                Spacer(Modifier.height(12.dp))
                Text("${if (zh) "密钥类型" else "Key type"}: ${entry.keyType}")
                Spacer(Modifier.height(8.dp))
                Text(if (zh) "指纹：" else "Fingerprint:")
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.fingerprint(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (zh) {
                        "请先核对该指纹与服务器密钥一致，再选择信任。"
                    } else {
                        "Verify this fingerprint matches the server's key before trusting."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) {
                Text(if (zh) "信任" else "Trust")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(if (zh) "取消" else "Cancel")
            }
        },
    )
}

@Composable
fun KeyChangedDialog(
    oldFingerprint: String,
    entry: KnownHostEntry,
    onAccept: () -> Unit,
    onDisconnect: () -> Unit,
    zh: Boolean = Locale.current.language == "zh",
) {
    AlertDialog(
        onDismissRequest = onDisconnect,
        title = {
            Text(
                if (zh) "主机密钥已更改" else "Host Key Changed",
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column {
                val hostDisplay = if (entry.port == 22) entry.hostname
                    else "${entry.hostname}:${entry.port}"
                Text(
                    if (zh) {
                        "$hostDisplay 的主机密钥已更改。" +
                            "这可能是服务器重装，也可能是中间人攻击。"
                    } else {
                        "The host key for $hostDisplay has changed. " +
                            "This could indicate a server reinstall or a man-in-the-middle attack."
                    },
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Text(if (zh) "旧指纹：" else "Old fingerprint:", style = MaterialTheme.typography.bodySmall)
                Text(
                    oldFingerprint,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(8.dp))
                Text(if (zh) "新指纹：" else "New fingerprint:", style = MaterialTheme.typography.bodySmall)
                Text(
                    entry.fingerprint(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(if (zh) "接受新密钥" else "Accept New Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDisconnect) {
                Text(if (zh) "断开连接" else "Disconnect")
            }
        },
    )
}
