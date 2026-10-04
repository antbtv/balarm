package com.antbtv.balarm.core.domain.testing

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeAlarmRepositoryTest {

    @Test
    fun `the reserved test id is never stored`() = runTest {
        val repository = FakeAlarmRepository()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.save(Alarm(id = AlarmId.TEST, time = LocalTime.NOON)) }
        }
        assertThat(repository.loadAll()).isEmpty()
    }
}
