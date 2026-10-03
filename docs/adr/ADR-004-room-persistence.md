# ADR-004: Хранение данных: Room 2.8 (не room3), схема v1, миграции

## Status
Accepted (2026-10-03, реализовано и проверено в M1). Предложено 2026-09-29. Реализует ADR-001 §1–2 для БД.

## Context
* M1 вводит первое персистентное хранилище: будильники и их runtime-состояние (следующий запланированный момент, snooze, последнее срабатывание). Всё это читается в `LOCKED_BOOT_COMPLETED` и в `RingingService` → только device-protected storage (ADR-001).
* ADR-003 оставил выбор: Room **2.8.5** (09.09.2026) или **room3 3.0.3** (09.09.2026). room3 — новый artifact/пакет `androidx.room3`, только KSP, только Kotlin, только `SQLiteDriver` (SupportSQLite — через wrapper), coroutines-first; пакет специально новый, чтобы 2.x и 3.x жили рядом. Room 2.x активно поддерживается, с 2.7 умеет `SQLiteDriver`, KSP2, Kotlin 2.
* Надёжность хранилища критична (G1), а инструменты миграционного тестирования, примеры Hilt и Robolectric для 2.x отработаны годами; у room3 ~полгода истории и неописанный testing-artifact.
* NFR-3 (APK ≤ 15 МБ): `BundledSQLiteDriver` добавляет нативную SQLite под каждую ABI.

## Decision
1. **Room 2.8.5** (`androidx.room:room-runtime`, `room-ktx` не нужен, `room-compiler` через KSP, `room-testing` для тестов), Gradle-плагин `androidx.room` со `schemaDirectory("$projectDir/schemas")`; схемы коммитятся (`core/data/schemas/`).
2. **Код «готов к room3»:** БД строится с `setDriver(AndroidSQLiteDriver())` (платформенная SQLite, без нативных библиотек); DAO — только `suspend`/`Flow`; миграции — через `Migration.migrate(connection: SQLiteConnection)`; никаких `SupportSQLiteDatabase`/`openHelper`. Переход на room3 тогда сводится к смене artifact/пакета — отдельным ADR, когда появится причина (KMP не нужен).
3. **Расположение:** `Room.databaseBuilder(@DeviceProtected context, BalarmDatabase::class.java, "balarm.db")`. `@DeviceProtected Context` предоставляется только в `:core:data` (ADR-001 §2). Однопроцессное приложение → без `enableMultiInstanceInvalidation`.
4. **Схема v1** (только то, что нужно M1; остальное добавляется аддитивно):
   ```sql
   CREATE TABLE alarm (
     id                  INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, -- монотонно, id не переиспользуются
     hour                INTEGER NOT NULL,          -- 0..23
     minute              INTEGER NOT NULL,          -- 0..59
     repeat_days         INTEGER NOT NULL,          -- битмаска ISO: Пн=1<<0 … Вс=1<<6; 0 = разовый
     label               TEXT    NOT NULL DEFAULT '',
     enabled             INTEGER NOT NULL,
     vibrate             INTEGER NOT NULL DEFAULT 1,
     snooze_interval_min INTEGER NOT NULL DEFAULT 5, -- 0 = snooze выключен
     snooze_limit        INTEGER NOT NULL DEFAULT 3  -- -1 = без лимита
   );
   CREATE TABLE alarm_runtime (
     alarm_id          INTEGER PRIMARY KEY NOT NULL REFERENCES alarm(id) ON DELETE CASCADE,
     next_trigger_at   INTEGER,                      -- epoch ms: ровно то, что стоит в AlarmManager
     next_trigger_kind TEXT    NOT NULL DEFAULT 'REGULAR', -- REGULAR | SNOOZE | CATCH_UP (ADR-006)
     snooze_count      INTEGER NOT NULL DEFAULT 0,
     last_fired_at     INTEGER                       -- epoch ms
   );
   ```
   Runtime отделён от настроек: редактор (M2) пишет `alarm`, движок — `alarm_runtime`, конфликтов нет. `isRinging` (PRD §6.4) **не персистится** — это состояние процесса `RingingService`.
5. **Эволюция:** будущие поля (`sound_id`, `volume`, `fade_in_sec` — M4; миссии — M5, отдельная таблица `alarm_mission` или JSON-колонка, решается ADR в M5; `show_quote`, `morning_briefing` — M6; `date`, `wake_up_check_min`, `skip_next_until` — M7) — только аддитивно, с `DEFAULT`, предпочтительно `@AutoMigration`. `fallbackToDestructiveMigration` **запрещён** в любой сборке. Каждая версия схемы → тест миграции `N-1 → N` на `MigrationTestHelper` (Robolectric, схемы подключены в assets тестового source set через `balarm.android.room`).
6. **DataStore** в M1 не подключается — нет потребителя (автостоп FR-RING-6 в M1 — константа). Когда появится (глобальные настройки), — `PreferenceDataStoreFactory.create { deContext.preferencesDataStoreFile(...) }` в `:core:data`, делегат `preferencesDataStore` запрещён (ADR-001 §2).

## Alternatives considered
* **room3 3.0.3 сразу** — нет будущей миграции пакета, но молодая линейка для самого критичного хранилища, меньше примеров для Robolectric/миграционных тестов, выигрыш (KMP) нам не нужен. Отложено: пункт 2 делает переход дешёвым.
* **Room 2.8 на SupportSQLite (по умолчанию)** — работает, но консервирует API, которого нет в room3. Отклонено.
* **`BundledSQLiteDriver`** — одинаковая SQLite на всех API, но +1–2 МБ на ABI (NFR-3). Отклонено.
* **Весь `Alarm` из PRD §6.4 в схеме v1** — меньше миграций, но колонки без потребителя и без тестов; миграции всё равно нужны и должны быть отработаны рано. Отклонено.
* **Runtime-поля в таблице `alarm`** — одна таблица, но гонки записи редактор ↔ движок. Отклонено.

## Consequences
* (+) Зрелый стек, понятные миграционные тесты; путь на room3 открыт.
* (+) Схема v1 минимальна; с M1 отлажен механизм экспорта схем и тестов миграций.
* (−) Каждая фича M2–M7 с новым полем = версия схемы + тест миграции (дёшево при AutoMigration).
* (−) Robolectric + `MigrationTestHelper` требует подключения `schemas/` как assets тестов — риск конфигурации AGP 9; фолбэк — инструментальный тест миграций.
* Проверка: Robolectric-тест, что файл БД лежит в DE (`context.isDeviceProtectedStorage`/путь `user_de`); тест `MigrationTestHelper.createDatabase(1)` + валидация схемы; R3, R9.

## Related
ADR-001, ADR-003, ADR-006; FR-REL-2, FR-REL-4, FR-EDIT-7, NFR-3, NFR-5; PRD §6.3, §6.4; R3, R4, R9.
