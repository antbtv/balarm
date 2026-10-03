package com.antbtv.balarm.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
