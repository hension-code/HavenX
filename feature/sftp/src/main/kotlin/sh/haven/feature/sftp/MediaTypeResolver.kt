package sh.haven.feature.sftp

object MediaTypeResolver {

    enum class MediaType { IMAGE, VIDEO, AUDIO, UNSUPPORTED }

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "m4v")
    private val AUDIO_EXTS = setOf("mp3", "flac", "aac", "ogg", "wav", "m4a", "opus", "oga")

    fun resolve(path: String): MediaType {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in IMAGE_EXTS -> MediaType.IMAGE
            in VIDEO_EXTS -> MediaType.VIDEO
            in AUDIO_EXTS -> MediaType.AUDIO
            else -> MediaType.UNSUPPORTED
        }
    }
}
