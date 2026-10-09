package com.antbtv.balarm.core.data.sound

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.antbtv.balarm.core.data.db.BalarmDatabase
import com.antbtv.balarm.core.data.db.CustomSoundEntity
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.sound.SoundSource
import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.takeCodePoints
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Библиотека своих мелодий (ADR-016 §4–5). Файлы — в device-protected storage; на пути звонка не участвует.
 * [defaultTitle] — название, если у файла нет читаемого имени (номер мелодии).
 */
class RoomSoundRepository(
    private val database: BalarmDatabase,
    private val files: DeviceProtectedSoundFileStore,
    private val opener: SoundSourceOpener,
    private val probe: AudioProbe,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val defaultTitle: (Int) -> String,
) : SoundRepository {

    private val dao = database.customSoundDao()

    override fun observeCustomSounds(): Flow<List<CustomSound>> =
        dao.observeAll().map { rows -> rows.mapNotNull(::toDomain) }

    override suspend fun getCustom(id: CustomSoundId): CustomSound? = dao.get(id.value)?.let(::toDomain)

    override suspend fun import(source: SoundSource): ImportResult = withContext(io) {
        val opened = opener.open(source) ?: return@withContext ImportResult.Failed("cannot open source")
        val limit = SoundRepository.IMPORT_LIMIT_BYTES
        if ((opened.sizeBytes ?: 0L) > limit) {
            opened.stream.close()
            return@withContext ImportResult.TooLarge(limit)
        }
        files.directory.mkdirs()
        val tmp = File(files.directory, "${DeviceProtectedSoundFileStore.TMP_PREFIX}${System.nanoTime()}")
        var committed = false
        try {
            val size = copyLimited(opened.stream, tmp, limit)
                ?: return@withContext ImportResult.TooLarge(limit)
            val durationMs = probe.durationMs(tmp) ?: return@withContext ImportResult.Unsupported
            val sound = commit(tmp, opened.displayName, durationMs, size)
            committed = true
            ImportResult.Imported(sound)
        } catch (e: IOException) {
            if (e.isNoSpace()) ImportResult.NoSpace else ImportResult.Failed(e.message.orEmpty())
        } finally {
            if (!committed) tmp.delete()
        }
    }

    /** Копирует с подсчётом байт; `null` — лимит превышен (файл остаётся для удаления вызывающим). */
    private suspend fun copyLimited(input: java.io.InputStream, target: File, limit: Long): Long? {
        val context = currentCoroutineContext()
        var total = 0L
        input.use { source ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    context.ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > limit) return null
                    out.write(buffer, 0, read)
                }
            }
        }
        return total
    }

    private suspend fun commit(tmp: File, displayName: String?, durationMs: Long, size: Long): CustomSound {
        val addedAt = clock.millis()
        val title = (displayName?.substringBeforeLast('.')?.trim()?.takeCodePoints(CustomSound.MAX_TITLE_LENGTH))
            .takeUnless { it.isNullOrBlank() }
        var id = 0L
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val fallback = title ?: defaultTitle(dao.count() + 1)
                id =
                    dao.insert(
                        CustomSoundEntity(
                            title = fallback,
                            durationMs = durationMs,
                            sizeBytes = size,
                            addedAt = addedAt,
                        ),
                    )
                if (!tmp.renameTo(files.fileOf(CustomSoundId(id)))) {
                    throw IOException("rename failed")
                }
            }
        }
        return checkNotNull(getCustom(CustomSoundId(id))) { "Imported sound disappeared" }
    }

    override suspend fun rename(id: CustomSoundId, title: String): Boolean {
        val clean = title.trim().takeCodePoints(CustomSound.MAX_TITLE_LENGTH)
        return clean.isNotEmpty() && dao.updateTitle(id.value, clean) > 0
    }

    override suspend fun usageCount(id: CustomSoundId): Int = dao.countAlarmsUsing(SoundRef.Custom(id).encode())

    override suspend fun delete(id: CustomSoundId): Int {
        val switched = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val count = dao.switchAlarmsSound(SoundRef.Custom(id).encode(), SoundRef.DEFAULT.encode())
                dao.delete(id.value)
                count
            }
        }
        // Звонящий прямо сейчас доигрывает: открытый FD переживает unlink.
        withContext(io) { files.fileOf(id).delete() }
        return switched
    }

    override suspend fun cleanUp() = withContext(io) {
        val dir = files.directory
        val rows = dao.getAll()
        val ids = rows.map { it.id }.toSet()
        dir.listFiles().orEmpty().forEach { file ->
            val id = file.name.toLongOrNull()
            if (file.name.startsWith(DeviceProtectedSoundFileStore.TMP_PREFIX) || id == null || id !in ids) {
                file.delete()
            }
        }
        rows.filterNot { files.fileOf(CustomSoundId(it.id)).exists() }.forEach { delete(CustomSoundId(it.id)) }
    }

    private fun toDomain(row: CustomSoundEntity): CustomSound? = runCatching {
        CustomSound(
            id = CustomSoundId(row.id),
            title = row.title.ifBlank { defaultTitle(row.id.toInt()) }.takeCodePoints(CustomSound.MAX_TITLE_LENGTH),
            duration = Duration.ofMillis(row.durationMs.coerceAtLeast(0)),
            sizeBytes = row.sizeBytes.coerceAtLeast(0),
            addedAt = Instant.ofEpochMilli(row.addedAt),
        )
    }.getOrNull()

    private fun IOException.isNoSpace(): Boolean = message.orEmpty().contains("ENOSPC") ||
        message.orEmpty().contains("No space left", ignoreCase = true)

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}
