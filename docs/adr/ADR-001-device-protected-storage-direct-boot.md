# ADR-001: Всё для звонка хранится в device-protected storage, цепочка звонка — directBootAware

## Status
Accepted (2026-09-28, утверждено вместе с планом M0). Предложено 2026-09-28. Принципиальное решение; детали реализации — в плане M1 (`:core:data`, `:core:alarm`).

## Context
* G1/FR-REL-2: будильник обязан сработать, если телефон перезагрузился ночью (автообновление ОС) и пользователь ещё не разблокировал его.
* До первой разблокировки (Direct Boot) доступно только device-protected (DE) хранилище; credential-encrypted (CE: `filesDir`, `getDatabasePath`, `SharedPreferences`/DataStore по умолчанию) недоступно. Запускаются только компоненты с `android:directBootAware="true"`.
* `AlarmManager` очищает все будильники при перезагрузке → перепланировать нужно в `LOCKED_BOOT_COMPLETED`, т.е. до разблокировки, читая расписание из DE.
* Данные приложения: время, дни, настройки звука, метки, цитаты, файлы мелодий. Секретов нет (нет аккаунтов, токенов, сети — NFR-4).

## Decision
1. **Единое хранилище = DE.** Room-БД, DataStore и каталог `files/sounds/` создаются только через `context.createDeviceProtectedStorageContext()`. CE-хранилище приложение не использует вообще (нет «двух миров» и миграций между ними).
2. **Единая точка доступа.** В DI предоставляется один `@DeviceProtected Context` (в `:core:data`); Room/DataStore/файлы строятся только от него. Запрещено вне `:core:data`: `context.filesDir`, `getDatabasePath`, `getSharedPreferences`, делегат `preferencesDataStore(...)` (он пишет в CE). Правило — в чек-листе reviewer; при появлении нарушений — кастомное detekt/lint-правило.
3. **directBootAware — поштучно, не на `<application>`.** Флаг ставится на компоненты цепочки звонка: ресиверы перепланирования (`LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, exact-alarm permission changed, `LOCALE_CHANGED`), `AlarmReceiver`, `RingingService`, `RingingActivity` (+ хост миссий). Главный экран, редактор, онбординг — не directBootAware (им DE не мешает, но запускать их до разблокировки незачем).
4. **Application — Direct-Boot-safe.** `Application.onCreate` и все eager-синглтоны Hilt выполняются и в Direct Boot, поэтому в них запрещён доступ к CE и к библиотекам, которые его используют (например, WorkManager/его БД; WorkManager в цепочке звонка не используется).
5. **Ресурсы для звонка — из APK или DE.** Встроенные мелодии (`res/raw`) и asset цитат доступны всегда; кастомные мелодии при импорте копируются в DE (FR-SND-2); цитаты сидятся в DE-БД.
6. **Резервное копирование.** `dataExtractionRules`/`fullBackupContent` должны явно включать домены `device_database`, `device_file`, `device_sharedpref` (иначе DE-данные не попадут в бэкап). Детали — M7/M8.

## Alternatives considered
* **CE-хранилище + зеркало расписания в DE** — две копии, синхронизация, риск рассинхрона ровно в критичном сценарии. Отклонено.
* **`<application android:directBootAware="true">`** — делает DB-aware все компоненты, включая UI, которому это не нужно; скрывает, какие компоненты реально критичны. Отклонено.
* **Не поддерживать Direct Boot** (перепланировать только на `BOOT_COMPLETED`) — нарушает G1 и сценарий R3. Отклонено.

## Consequences
* (+) R3 проходит по построению: `LOCKED_BOOT_COMPLETED` читает DE-БД и вызывает `setAlarmClock`.
* (+) Одно хранилище, нет миграций CE↔DE.
* (−) Данные (метки, пользовательские цитаты) не защищены паролем экрана блокировки. Принято: секретов нет; отражено в политике конфиденциальности (M8).
* (−) Любая новая библиотека с собственным хранилищем (WorkManager, сторонние кэши) должна проверяться на Direct Boot, если используется в цепочке звонка.
* Проверка: сценарии R3, R4, R9; Robolectric-тест, что фабрика БД/DataStore получает DE-контекст; M4 DoD — кастомная мелодия играет после перезагрузки до разблокировки.

## Related
FR-REL-2, FR-REL-3, FR-SND-2, FR-QOT-1, NFR-4, PRD §6.3; сценарии R3, R4, R9, R15.
