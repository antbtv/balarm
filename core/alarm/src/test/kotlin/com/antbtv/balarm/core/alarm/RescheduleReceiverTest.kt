package com.antbtv.balarm.core.alarm

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.IOException
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Граф — `TestAlarmModule`: настоящий движок на фейках; результат виден по событиям лога. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RescheduleReceiverTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var repository: FakeAlarmRepository

    @Inject lateinit var recording: RecordingEventLog

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = hilt.inject()

    @Test
    fun `each system event reschedules with its own reason`() {
        val expected = mapOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED to RescheduleReason.LOCKED_BOOT,
            Intent.ACTION_BOOT_COMPLETED to RescheduleReason.BOOT,
            Intent.ACTION_TIME_CHANGED to RescheduleReason.TIME_SET,
            Intent.ACTION_TIMEZONE_CHANGED to RescheduleReason.TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED to RescheduleReason.PACKAGE_REPLACED,
            Intent.ACTION_LOCALE_CHANGED to RescheduleReason.LOCALE_CHANGED,
        )

        expected.keys.forEach { deliver(Intent(it)) }

        assertThat(recording.events.filterIsInstance<AlarmEvent.RescheduledAll>().map { it.reason })
            .containsExactlyElementsIn(expected.values).inOrder()
    }

    @Test
    fun `unknown action is ignored without holding the broadcast`() {
        val receiver = RescheduleReceiver().withPendingResult()

        receiver.onReceive(app, Intent("com.example.OTHER"))

        assertThat(shadowOf(receiver).wentAsync()).isFalse()
        assertThat(recording.events).isEmpty()
    }

    @Test
    fun `receiver never starts ringing`() {
        deliver(Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(shadowOf(app).nextStartedService).isNull()
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test
    fun `failure is logged and the broadcast is still finished`() {
        repository.failOnLoad = IOException("db")

        // deliver ждёт finish() с таймаутом: без него после падения rescheduleAll тест упал бы по TimeoutException.
        deliver(Intent(Intent.ACTION_TIMEZONE_CHANGED))

        assertThat(recording.events).containsExactly(
            AlarmEvent.RescheduleAllFailed(RescheduleReason.TIMEZONE_CHANGED, "IOException"),
        )
    }

    private fun deliver(intent: Intent) = RescheduleReceiver().deliverAndAwaitFinish(app, intent, TIMEOUT_S)

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
