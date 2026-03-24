package sh.haven.feature.sftp

object MediaTypeResolver {

    enum class MediaType { IMAGE, VIDEO, AUDIO, TEXT, UNSUPPORTED }

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "m4v")
    private val AUDIO_EXTS = setOf("mp3", "flac", "aac", "ogg", "wav", "m4a", "opus", "oga")
    private val TEXT_EXTS = setOf(
        "txt", "md", "csv", "json", "xml", "html", "css", "js", "ts", "kt", "java", "py", 
        "c", "cpp", "h", "hpp", "sh", "bat", "ps1", "yml", "yaml", "ini", "conf", "log", "rtf"
    )

    fun resolve(path: String): MediaType {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in IMAGE_EXTS -> MediaType.IMAGE
            in VIDEO_EXTS -> MediaType.VIDEO
            in AUDIO_EXTS -> MediaType.AUDIO
            in TEXT_EXTS -> MediaType.TEXT
            else -> MediaType.UNSUPPORTED
        }
    }
}
