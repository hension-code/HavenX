package sh.haven.core.data.preferences

import org.json.JSONArray
import org.json.JSONObject

data class TerminalComboKey(
    val id: String,
    val label: String,
    val keys: String,
    val send: String,
)

object TerminalComboKeys {
    val PRESETS = listOf(
        preset("ctrl_c", "Ctrl+C", "Ctrl+C", "\u0003"),
        preset("ctrl_d", "Ctrl+D", "Ctrl+D", "\u0004"),
        preset("ctrl_z", "Ctrl+Z", "Ctrl+Z", "\u001A"),
        preset("ctrl_l", "Ctrl+L", "Ctrl+L", "\u000C"),
        preset("ctrl_a", "Ctrl+A", "Ctrl+A", "\u0001"),
        preset("ctrl_e", "Ctrl+E", "Ctrl+E", "\u0005"),
        preset("ctrl_u", "Ctrl+U", "Ctrl+U", "\u0015"),
        preset("ctrl_k", "Ctrl+K", "Ctrl+K", "\u000B"),
        preset("ctrl_r", "Ctrl+R", "Ctrl+R", "\u0012"),
        preset("ctrl_w", "Ctrl+W", "Ctrl+W", "\u0017"),
        preset("ctrl_s", "Ctrl+S", "Ctrl+S", "\u0013"),
        preset("ctrl_q", "Ctrl+Q", "Ctrl+Q", "\u0011"),
    )

    fun encodeKeys(keys: String): String? {
        val normalized = keys.trim()
        if (normalized.isBlank()) return null

        val parts = normalized.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        var ctrl = false
        var alt = false
        var token: String? = null
        for (part in parts) {
            when (part.lowercase()) {
                "ctrl", "control" -> ctrl = true
                "alt", "option", "meta" -> alt = true
                else -> token = part
            }
        }

        val base = when (val raw = token ?: return null) {
            "Esc", "Escape", "esc", "escape" -> "\u001B"
            "Tab", "tab" -> "\u0009"
            "Enter", "Return", "enter", "return" -> "\r"
            "Backspace", "backspace" -> "\u007F"
            "Space", "space" -> " "
            else -> {
                if (raw.length != 1) return null
                raw
            }
        }

        var encoded = base
        if (ctrl) {
            if (base.length != 1) return null
            val code = base[0].uppercaseChar().code
            encoded = when (code) {
                in 0x40..0x5F -> (code and 0x1F).toChar().toString()
                0x3F -> "\u007F"
                else -> return null
            }
        }
        if (alt) {
            encoded = "\u001B$encoded"
        }
        return encoded
    }

    fun fromJson(json: String?): List<TerminalComboKey> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val label = obj.optString("label")
                    val keys = obj.optString("keys")
                    val send = obj.optString("send")
                    if (id.isBlank() || label.isBlank() || keys.isBlank() || send.isBlank()) continue
                    add(TerminalComboKey(id, label, keys, send))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun toJson(keys: List<TerminalComboKey>): String {
        val array = JSONArray()
        keys.forEach { key ->
            array.put(
                JSONObject()
                    .put("id", key.id)
                    .put("label", key.label)
                    .put("keys", key.keys)
                    .put("send", key.send),
            )
        }
        return array.toString()
    }

    private fun preset(id: String, label: String, keys: String, send: String) =
        TerminalComboKey(id = id, label = label, keys = keys, send = send)
}
