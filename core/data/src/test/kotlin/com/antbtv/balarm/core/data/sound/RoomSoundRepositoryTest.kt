package com.antbtv.balarm.core.data.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.data.RoomAlarmRepository
import com.antbtv.balarm.core.data.db.BalarmDatabase
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.sound.SoundSource
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RoomSoundRepositoryTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val deContext = appContext.createDeviceProtectedStorageContext()
    private val database = BalarmDatabase.create(deContext)
    private val files = DeviceProtectedSoundFileStore(deContext)
    private val alarms = RoomAlarmRepository(database)
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    private var sources = mutableMapOf<String, OpenedSource>()
    private var probeResult: Long? = 5_000
    private val repository = RoomSoundRepository(
        database = database,
        files = files,
        opener = { sources[it.uri] },
        probe = { probeResult },
        clock = clock,
        io = dispatcher,
        defaultTitle = { "Melody $it" },
    )

    @After
    fun tearDown() {
        database.close()
        deContext.deleteDatabase(BalarmDatabase.NAME)
        files.directory.deleteRecursively()
    }

    private fun source(
        uri: String = "content://a",
        name: String? = "wake.mp3",
        size: Long? = null,
        stream: InputStream = ByteArrayInputStream(ByteArray(100) { it.toByte() }),
    ): SoundSource {
        sources[uri] = OpenedSource(name, size, stream)
        return SoundSource(uri)
    }

    private suspend fun importOk(name: String? = "wake.mp3"): CustomSoundId =
        (repository.import(source(name = name)) as ImportResult.Imported).sound.id

    @Test
    fun `import copies the file into device protected storage and names it by id`() = runTest {
        val result = repository.import(source(name = "Утро в лесу.ogg")) as ImportResult.Imported

        val file = files.fileOf(result.sound.id)
        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isEqualTo(100)
        assertThat(deContext.isDeviceProtectedStorage).isTrue()
        assertThat(file.absolutePath).startsWith(deContext.filesDir.absolutePath)
        assertThat(result.sound.title).isEqualTo("Утро в лесу")
        assertThat(result.sound.sizeBytes).isEqualTo(100)
        assertThat(result.sound.duration.toMillis()).isEqualTo(5_000)
        assertThat(repository.observeCustomSounds().first()).containsExactly(result.sound)
        assertThat(files.directory.listFiles()!!.map { it.name }).containsExactly(result.sound.id.value.toString())
    }

    @Test
    fun `blank display name falls back to numbered default title`() = runTest {
        val first = importOk(name = null)
        val second = importOk(name = "  .mp3")

        assertThat(repository.getCustom(first)!!.title).isEqualTo("Melody 1")
        assertThat(repository.getCustom(second)!!.title).isEqualTo("Melody 2")
    }

    @Test
    fun `long title is cut to 40 code points`() = runTest {
        val id = importOk(name = "a".repeat(60) + ".wav")

        assertThat(repository.getCustom(id)!!.title).hasLength(40)
    }

    @Test
    fun `declared size over the limit is rejected without copying`() = runTest {
        val result = repository.import(source(size = SoundRepository.IMPORT_LIMIT_BYTES + 1))

        assertThat(result).isEqualTo(ImportResult.TooLarge(SoundRepository.IMPORT_LIMIT_BYTES))
        assertThat(files.directory.listFiles().orEmpty()).isEmpty()
        assertThat(repository.observeCustomSounds().first()).isEmpty()
    }

    @Test
    fun `stream longer than the limit is cut off and the temp file removed`() = runTest {
        val endless = object : InputStream() {
            override fun read(): Int = 1

            override fun read(b: ByteArray, off: Int, len: Int): Int = len
        }

        val result = repository.import(source(size = null, stream = endless))

        assertThat(result).isInstanceOf(ImportResult.TooLarge::class.java)
        assertThat(files.directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `file the device cannot decode is unsupported and leaves nothing behind`() = runTest {
        probeResult = null

        val result = repository.import(source())

        assertThat(result).isEqualTo(ImportResult.Unsupported)
        assertThat(files.directory.listFiles().orEmpty()).isEmpty()
        assertThat(repository.observeCustomSounds().first()).isEmpty()
    }

    @Test
    fun `no space on device is reported and cleaned up`() = runTest {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("write failed: ENOSPC (No space left on device)")
        }

        val result = repository.import(source(stream = failing))

        assertThat(result).isEqualTo(ImportResult.NoSpace)
        assertThat(files.directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `unopenable source fails`() = runTest {
        assertThat(repository.import(SoundSource("content://missing"))).isInstanceOf(ImportResult.Failed::class.java)
    }

    @Test
    fun `cancelled import removes the temp file`() = runTest {
        val started = CompletableDeferred<Unit>()
        val blocking = object : InputStream() {
            override fun read(): Int = 1

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                started.complete(Unit)
                return len
            }
        }
        val job = launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            repository.import(source(stream = blocking))
        }

        started.await()
        job.cancelAndJoin()

        assertThat(files.directory.listFiles().orEmpty()).isEmpty()
        assertThat(repository.observeCustomSounds().first()).isEmpty()
    }

    @Test
    fun `rename trims and rejects blank`() = runTest {
        val id = importOk()

        assertThat(repository.rename(id, "  Новая  ")).isTrue()
        assertThat(repository.getCustom(id)!!.title).isEqualTo("Новая")
        assertThat(repository.rename(id, "   ")).isFalse()
        assertThat(repository.rename(CustomSoundId(999), "x")).isFalse()
    }

    @Test
    fun `delete switches alarms using the sound to default and removes the file`() = runTest {
        val id = importOk()
        val other = importOk()
        val custom = Alarm(time = LocalTime.of(7, 0), sound = SoundSettings(sound = SoundRef.Custom(id)))
        val a = alarms.save(custom)
        val b = alarms.save(custom.copy(time = LocalTime.of(8, 0)))
        val c = alarms.save(custom.copy(time = LocalTime.of(9, 0), sound = SoundSettings(SoundRef.Custom(other))))

        assertThat(repository.usageCount(id)).isEqualTo(2)
        val switched = repository.delete(id)

        assertThat(switched).isEqualTo(2)
        assertThat(alarms.get(a)!!.sound.sound).isEqualTo(SoundRef.DEFAULT)
        assertThat(alarms.get(b)!!.sound.sound).isEqualTo(SoundRef.DEFAULT)
        assertThat(alarms.get(c)!!.sound.sound).isEqualTo(SoundRef.Custom(other))
        assertThat(files.fileOf(id).exists()).isFalse()
        assertThat(files.fileOf(other).exists()).isTrue()
        assertThat(repository.getCustom(id)).isNull()
    }

    @Test
    fun `cleanUp removes temp files orphan files and rows without files`() = runTest {
        val kept = importOk()
        val rowWithoutFile = importOk()
        val alarm = alarms.save(
            Alarm(time = LocalTime.of(7, 0), sound = SoundSettings(sound = SoundRef.Custom(rowWithoutFile))),
        )
        files.fileOf(rowWithoutFile).delete()
        val tmp = java.io.File(files.directory, ".tmp-1").apply { writeText("x") }
        val orphan = java.io.File(files.directory, "777").apply { writeText("x") }

        repository.cleanUp()

        assertThat(tmp.exists()).isFalse()
        assertThat(orphan.exists()).isFalse()
        assertThat(files.fileOf(kept).exists()).isTrue()
        assertThat(repository.observeCustomSounds().first().map { it.id }).containsExactly(kept)
        assertThat(alarms.get(alarm)!!.sound.sound).isEqualTo(SoundRef.DEFAULT)
    }
}
