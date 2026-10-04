package com.antbtv.balarm.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.data.db.AlarmEntity
import com.antbtv.balarm.core.data.db.BalarmDatabase
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.TriggerKind
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAlarmRepositoryTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val deContext = appContext.createDeviceProtectedStorageContext()
    private val database = BalarmDatabase.create(deContext)
    private val repository = RoomAlarmRepository(database)

    private val alarm = Alarm(
        time = LocalTime.of(6, 30),
        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        label = "Тренировка",
        snooze = SnoozeSettings(interval = null, maxCount = null),
    )

    @After
    fun tearDown() {
        database.close()
        deContext.deleteDatabase(BalarmDatabase.NAME)
    }

    @Test
    fun `database file lives in device protected storage`() = runTest {
        repository.save(alarm)

        assertThat(deContext.isDeviceProtectedStorage).isTrue()
        assertThat(deContext.getDatabasePath(BalarmDatabase.NAME).exists()).isTrue()
    }

    @Test
    fun `save inserts with a new id and round trips every field`() = runTest {
        val id = repository.save(alarm)

        assertThat(id.isSaved).isTrue()
        assertThat(repository.get(id)).isEqualTo(alarm.copy(id = id))
    }

    @Test
    fun `save of an existing alarm updates it in place`() = runTest {
        val id = repository.save(alarm)

        val again = repository.save(alarm.copy(id = id, time = LocalTime.of(7, 0)))

        assertThat(again).isEqualTo(id)
        assertThat(repository.get(id)?.time).isEqualTo(LocalTime.of(7, 0))
        assertThat(repository.loadAll()).hasSize(1)
    }

    @Test
    fun `the reserved test id is never stored`() = runTest {
        val failure = runCatching { repository.save(Alarm(id = AlarmId.TEST, time = LocalTime.of(6, 0))) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.loadAll()).isEmpty()
    }

    @Test
    fun `ids are never reused after delete`() = runTest {
        val first = repository.save(alarm)
        repository.delete(first)

        val second = repository.save(alarm)

        assertThat(second.value).isGreaterThan(first.value)
    }

    @Test
    fun `runtime state round trips and is deleted together with the alarm`() = runTest {
        val id = repository.save(alarm)
        val runtime =
            AlarmRuntimeState(id, Instant.ofEpochMilli(1_000), TriggerKind.SNOOZE, 2, Instant.ofEpochMilli(500))

        repository.updateRuntime(runtime)
        assertThat(repository.getRuntime(id)).isEqualTo(runtime)
        assertThat(repository.loadAll().single().runtime).isEqualTo(runtime)

        repository.delete(id)
        assertThat(repository.getRuntime(id)).isNull()
    }

    @Test
    fun `set enabled flips only the flag`() = runTest {
        val id = repository.save(alarm)

        repository.setEnabled(id, false)

        assertThat(repository.get(id)).isEqualTo(alarm.copy(id = id, enabled = false))
    }

    @Test
    fun `observe emits alarms sorted by time`() = runTest {
        repository.save(alarm.copy(time = LocalTime.of(9, 0)))
        repository.save(alarm.copy(time = LocalTime.of(5, 0)))

        val times = repository.observeAlarms().first().map { it.time }

        assertThat(times).containsExactly(LocalTime.of(5, 0), LocalTime.of(9, 0)).inOrder()
    }

    @Test
    fun `transaction commits all writes`() = runTest {
        val id = repository.transaction {
            val saved = repository.save(alarm)
            repository.updateRuntime(AlarmRuntimeState(saved, Instant.ofEpochMilli(42)))
            saved
        }

        assertThat(repository.getRuntime(id)?.nextTriggerAt).isEqualTo(Instant.ofEpochMilli(42))
    }

    @Test
    fun `a corrupted row does not break loading the other alarms`() = runTest {
        repository.save(alarm)
        database.alarmDao().insert(
            AlarmEntity(
                hour = 99,
                minute = -5,
                repeatDays = 0xFF,
                label = "x".repeat(500),
                enabled = true,
                vibrate = true,
                snoozeIntervalMin = 999,
                snoozeLimit = 50,
            ),
        )

        val loaded = repository.loadAll()

        assertThat(loaded).hasSize(2)
        assertThat(loaded.map { it.alarm.id }).doesNotContain(AlarmId.UNSAVED)
    }
}
