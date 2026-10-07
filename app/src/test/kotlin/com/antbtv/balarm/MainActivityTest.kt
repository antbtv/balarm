package com.antbtv.balarm

import android.app.Application
import android.database.sqlite.SQLiteException
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.feature.ringing.RingingActivity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var repository: FakeAlarmRepository

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = hilt.inject()

    @Test
    fun `launch reschedules all alarms`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            shadowOf(Looper.getMainLooper()).idle()

            assertThat(ShadowLog.getLogsForTag("Balarm").map { it.msg })
                .contains("RESCHEDULE_ALL reason=APP_LAUNCH count=0")
        }
    }

    @Test
    fun `broken database on launch is logged, not a crash`() {
        repository.failOnLoad = SQLiteException("corrupt")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()

            assertThat(scenario.state).isAtLeast(Lifecycle.State.STARTED)
            assertThat(ShadowLog.getLogsForTag("Balarm").map { it.msg })
                .contains("RESCHEDULE_ALL_FAILED reason=APP_LAUNCH error=SQLiteException")
        }
    }

    @Test
    fun `open app leads to the ringing screen only while ringing`() {
        val intents = AppUiIntents(app)
        val ringing = RingingState.Ringing(
            alarm = Alarm(id = AlarmId(1), time = LocalTime.of(7, 0)),
            startedAt = Instant.EPOCH,
            canSnooze = true,
            snoozesLeft = 3,
        )

        assertThat(ringingRedirect(ringing, intents)?.component?.className).isEqualTo(RingingActivity::class.java.name)
        assertThat(ringingRedirect(RingingState.Idle, intents)).isNull()
    }

    /** ADR-013 §5: разрешения запрашивает онбординг; временный запрос из M1 удалён. */
    @Test
    fun `launch and recreation request no runtime permission`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { assertThat(shadowOf(it).lastRequestedPermission).isNull() }

            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { assertThat(shadowOf(it).lastRequestedPermission).isNull() }
        }
    }
}
