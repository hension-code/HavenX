package sh.haven.feature.sftp

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val PREVIEW_TAG = "LocalMediaPreviewServer"

data class PreviewByteSource(
    val totalSize: Long,
    val mimeType: String,
    val readRange: (start: Long, endInclusive: Long, output: OutputStream) -> Unit,
)

object LocalMediaPreviewServer {
    private var port: Int = -1
    private var started = false
    private val sources = ConcurrentHashMap<String, PreviewByteSource>()
    private val pool = Executors.newCachedThreadPool()

    @Synchronized
    fun register(source: PreviewByteSource): String {
        ensureStarted()
        val token = UUID.randomUUID().toString()
        sources[token] = source
        if (sources.size > 64) {
            val first = sources.keys.firstOrNull()
            if (first != null) sources.remove(first)
        }
        return "http://localhost:$port/media/$token"
    }

    @Synchronized
    private fun ensureStarted() {
        if (started) return
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        port = server.localPort
        started = true
        pool.execute {
            while (!server.isClosed) {
                try {
                    val client = server.accept()
                    pool.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (!server.isClosed) Log.e(PREVIEW_TAG, "accept failed", e)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 15000
                val input = BufferedInputStream(s.getInputStream())
                val output = BufferedOutputStream(s.getOutputStream())
                val request = readLine(input) ?: return
                val parts = request.split(" ")
                if (parts.size < 2) return writeError(output, 400, "Bad Request")
                val method = parts[0].uppercase(Locale.ROOT)
                if (method != "GET" && method != "HEAD") return writeError(output, 405, "Method Not Allowed")
                val path = parts[1]

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isBlank()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase(Locale.ROOT)] = line.substring(idx + 1).trim()
                }

                val token = path.removePrefix("/media/").substringBefore('?')
                val source = sources[token] ?: return writeError(output, 404, "Not Found")
                if (source.totalSize <= 0L) {
                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: ${source.mimeType}\r\nAccept-Ranges: bytes\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    output.write(resp.toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                    return
                }
                val (start, end, partial) = parseRange(headers["range"], source.totalSize)
                if (start < 0 || end < start || end >= source.totalSize) {
                    return writeRangeNotSatisfiable(output, source.totalSize)
                }

                val status = if (partial) "206 Partial Content" else "200 OK"
                val len = end - start + 1
                val builder = StringBuilder()
                    .append("HTTP/1.1 ").append(status).append("\r\n")
                    .append("Content-Type: ").append(source.mimeType).append("\r\n")
                    .append("Accept-Ranges: bytes\r\n")
                    .append("Content-Length: ").append(len).append("\r\n")
                    .append("Connection: close\r\n")
                if (partial) builder.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(source.totalSize).append("\r\n")
                builder.append("\r\n")
                output.write(builder.toString().toByteArray(StandardCharsets.UTF_8))
                output.flush()
                if (method == "GET") {
                    source.readRange(start, end, output)
                    output.flush()
                }
            } catch (e: Exception) {
                Log.e(PREVIEW_TAG, "handle failed", e)
            }
        }
    }

    private fun parseRange(rangeHeader: String?, total: Long): Triple<Long, Long, Boolean> {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) return Triple(0, total - 1, false)
        val value = rangeHeader.removePrefix("bytes=").trim()
        val dash = value.indexOf('-')
        if (dash < 0) return Triple(-1, -1, true)
        val startText = value.substring(0, dash).trim()
        val endText = value.substring(dash + 1).trim()
        if (startText.isEmpty()) {
            val suffix = endText.toLongOrNull() ?: return Triple(-1, -1, true)
            val start = (total - suffix).coerceAtLeast(0)
            return Triple(start, total - 1, true)
        }
        val start = startText.toLongOrNull() ?: return Triple(-1, -1, true)
        val end = if (endText.isEmpty()) total - 1 else (endText.toLongOrNull() ?: return Triple(-1, -1, true))
        return Triple(start, minOf(end, total - 1), true)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream(128)
        while (true) {
            val b = input.read()
            if (b == -1) return if (bytes.size() == 0) null else String(bytes.toByteArray(), StandardCharsets.UTF_8)
            if (b == '\n'.code) break
            if (b != '\r'.code) bytes.write(b)
            if (bytes.size() > 8192) break
        }
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun writeError(out: OutputStream, code: Int, message: String) {
        val body = "$code $message"
        val resp = "HTTP/1.1 $body\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
        out.write(resp.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun writeRangeNotSatisfiable(out: OutputStream, total: Long) {
        val body = "416 Range Not Satisfiable"
        val resp = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
        out.write(resp.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }
}
