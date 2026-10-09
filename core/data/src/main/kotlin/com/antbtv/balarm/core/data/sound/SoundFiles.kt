package com.antbtv.balarm.core.data.sound

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.antbtv.balarm.core.data.di.DeviceProtected
import com.antbtv.balarm.core.domain.sound.SoundFileStore
import com.antbtv.balarm.core.domain.sound.SoundSource
import com.antbtv.balarm.core.model.CustomSoundId
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Свои мелодии — `DE files/sounds/<id>` (ADR-016 §3). */
@Singleton
class DeviceProtectedSoundFileStore @Inject constructor(@DeviceProtected private val context: Context) :
    SoundFileStore {
    val directory: File get() = File(context.filesDir, DIRECTORY)

    override fun fileOf(id: CustomSoundId): File = File(directory, id.value.toString())

    companion object {
        const val DIRECTORY = "sounds"
        const val TMP_PREFIX = ".tmp-"
    }
}

/** Открытый источник импорта: имя и размер (если провайдер их сообщил) и поток данных. */
class OpenedSource(val displayName: String?, val sizeBytes: Long?, val stream: InputStream)

/** Доступ к `content://` из SAF; отдельный интерфейс, чтобы тестировать импорт без ContentResolver. */
fun interface SoundSourceOpener {
    fun open(source: SoundSource): OpenedSource?
}

@Singleton
class ContentResolverSoundSourceOpener @Inject constructor(@DeviceProtected private val context: Context) :
    SoundSourceOpener {
    override fun open(source: SoundSource): OpenedSource? {
        val uri = Uri.parse(source.uri)
        val resolver = context.applicationContext.contentResolver
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        size = if (cursor.isNull(1)) null else cursor.getLong(1)
                    }
                }
        }
        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return null
        return OpenedSource(name, size, stream)
    }
}

/** Проверка, что файл — аудио, которое умеет декодировать устройство. */
fun interface AudioProbe {
    /** Длительность в мс или `null`, если нет аудиодорожки/декодера. */
    fun durationMs(file: File): Long?
}

@Singleton
class MediaAudioProbe @Inject constructor() : AudioProbe {
    override fun durationMs(file: File): Long? {
        val extractor = MediaExtractor()
        return try {
            FileInputStream(file).use { extractor.setDataSource(it.fd) }
            val format = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            val decodable = format != null && MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .findDecoderForFormat(format) != null
            if (!decodable || !format.containsKey(MediaFormat.KEY_DURATION)) {
                null
            } else {
                (format.getLong(MediaFormat.KEY_DURATION) / MICROS_PER_MILLI).takeIf { it > 0 }
            }
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private companion object {
        const val MICROS_PER_MILLI = 1000
    }
}
