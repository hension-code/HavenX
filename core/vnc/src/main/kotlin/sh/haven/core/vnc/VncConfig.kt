package sh.haven.core.vnc

import android.graphics.Bitmap

/**
 * Configuration for a VNC client connection.
 */
class VncConfig {
    var passwordSupplier: (() -> String)? = null
    var usernameSupplier: (() -> String)? = null
    var onScreenUpdate: ((Bitmap) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null
    var onBell: (() -> Unit)? = null
    var onRemoteClipboard: ((String) -> Unit)? = null
    var shared: Boolean = true
    var targetFps: Int = 30
    var colorDepth: ColorDepth = ColorDepth.BPP_24_TRUE

    /**
     * When true, refuse the "no authentication" security type (RFB type 1).
     * Set for direct (non-tunneled) connections where a password was supplied:
     * a server silently dropping to no-auth despite a configured password may
     * indicate a downgrade attack or a misconfiguration. Left false for SSH
     * tunnels, where the tunnel itself authenticates the server and a
     * no-auth VNC server behind it is a legitimate configuration.
     */
    var requireAuth: Boolean = false
}

enum class ColorDepth(
    val bitsPerPixel: Int,
    val depth: Int,
    val trueColor: Boolean,
    val redMax: Int,
    val greenMax: Int,
    val blueMax: Int,
    val redShift: Int,
    val greenShift: Int,
    val blueShift: Int,
) {
    BPP_8_INDEXED(8, 8, false, 0, 0, 0, 0, 0, 0),
    BPP_8_TRUE(8, 8, true, 7, 3, 7, 0, 6, 3),
    BPP_16_TRUE(16, 16, true, 31, 63, 31, 11, 5, 0),
    BPP_24_TRUE(32, 24, true, 255, 255, 255, 16, 8, 0),
}
