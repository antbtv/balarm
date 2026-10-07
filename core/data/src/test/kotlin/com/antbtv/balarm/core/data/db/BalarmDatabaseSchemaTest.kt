package com.antbtv.balarm.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Экспортированная схема v1 совпадает с тем, что генерирует Room. База для будущих тестов миграций N-1 → N. */
@RunWith(AndroidJUnit4::class)
class BalarmDatabaseSchemaTest {

    private val file = ApplicationProvider.getApplicationContext<android.content.Context>()
        .createDeviceProtectedStorageContext()
        .getDatabasePath("schema-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = file,
        driver = AndroidSQLiteDriver(),
        databaseClass = BalarmDatabase::class,
    )

    @Test
    fun `version 1 schema is valid`() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(1, emptyList()).close()
    }

    @Test
    fun `auto migration 1 to 2 keeps data and defaults schedule_failed to 0`() {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                "INSERT INTO alarm (id, hour, minute, repeat_days, label, enabled, vibrate, snooze_interval_min, " +
                    "snooze_limit) VALUES (7, 6, 30, 0, 'x', 1, 1, 5, 3)",
            )
            db.execSQL(
                "INSERT INTO alarm_runtime (alarm_id, next_trigger_at, next_trigger_kind, snooze_count, " +
                    "last_fired_at) VALUES (7, 1000, 'REGULAR', 0, NULL)",
            )
        }

        helper.runMigrationsAndValidate(2, emptyList()).use { db ->
            db.prepare("SELECT hour, minute, label FROM alarm WHERE id = 7").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(6)
                assertThat(it.getText(2)).isEqualTo("x")
            }
            db.prepare("SELECT next_trigger_at, schedule_failed FROM alarm_runtime WHERE alarm_id = 7").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(1000)
                assertThat(it.getLong(1)).isEqualTo(0)
            }
        }
    }
}
