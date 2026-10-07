# ADR-012: Здоровье будильника: модель статусов, PermissionHealthChecker, баннер и «Исправить»

## Status
Accepted (2026-10-07, M3 завершён; уточнения по итогам реализации — в конце файла). Реализует PRD §3.7 (таблица разрешений, FR-REL-7), FR-LIST-5, сценарий R12; закрывает ADR-011 §11 Б вместе с ADR-015.

## Context
* M3 (PRD §8): онбординг 7 шагов, `PermissionHealthChecker`, баннер FR-LIST-5, экран «Здоровье будильника» (Настройки → Надёжность), «Тестовый будильник через 1 минуту». DoD: все статусы корректны на API 34 и 37.
* minSdk 34 (ADR-003): `POST_NOTIFICATIONS`, `canUseFullScreenIntent()`, `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` есть всегда; `USE_EXACT_ALARM` выдаётся при установке и пользователем не отзывается (ADR-003, поправка; FR-REL-3) — `canScheduleExactAlarms()` практически всегда `true`.
* Что реально ломает звонок на стоковом Android (проверено по документации на 2026-10-07):
  * нет `POST_NOTIFICATIONS` или канал `alarm_ringing` выключен/понижен — FGS работает, звук есть, **экрана звонка нет** (ADR-007 §8);
  * отозван `USE_FULL_SCREEN_INTENT` — heads-up вместо экрана;
  * **«Ограничено» в расходе батареи** (`ActivityManager.isBackgroundRestricted()`): по AOSP «App power management» ограниченное приложение не получает alarms/jobs в фоне и не может запускать FGS — будильник не сработает. В таблице PRD §3.7 этого пункта нет;
  * `scheduled = false` при планировании (ADR-011 §11 Б).
* Что ухудшает, но не ломает: нет overlay (`Settings.canDrawOverlays`) — при включённом разблокированном экране только heads-up и нет возврата экрана (FR-RING-5); оптимизация батареи (`isIgnoringBatteryOptimizations`) — `setAlarmClock` и так исключён из Doze, критично в основном на OEM; DND в режиме «полная тишина» или без категории «будильники»; громкость `STREAM_ALARM` = 0 до M4 (FR-SND-7 выставит громкость будильника сама).
* **Overlay сейчас ни на что не влияет:** в коде нет `startActivity` экрана звонка при `canDrawOverlays` (FR-REL-5 «дублирующе через startActivity») и `SYSTEM_ALERT_WINDOW` нет в манифесте. Просить разрешение, которое ничего не делает, нельзя. BAL-исключение «пользователь выдал `SYSTEM_ALERT_WINDOW`» действует на API 34–37 (developer.android.com, «Restrictions on starting activities from the background»).
* Принципы: ViewModel не знает `Context`; `:core:domain` — чистый Kotlin; флаги на ядро надёжности запрещены (FR-FLAG-4).

## Decision
1. **Модель — в `:core:domain` (пакет `health`), чистый Kotlin:**
   ```kotlin
   /** Порядок = порядок онбординга и экрана здоровья. */
   enum class HealthItem(val severity: Severity) {
       NOTIFICATIONS(CRITICAL),          // разрешение приложения И канал alarm_ringing с importance ≥ HIGH
       EXACT_ALARMS(CRITICAL),           // canScheduleExactAlarms()
       FULL_SCREEN_INTENT(CRITICAL),     // canUseFullScreenIntent()
       OVERLAY(RECOMMENDED),             // canDrawOverlays()
       BATTERY_OPTIMIZATION(RECOMMENDED),// isIgnoringBatteryOptimizations(pkg)
       BACKGROUND_RESTRICTION(CRITICAL), // !isBackgroundRestricted()
       OEM_BACKGROUND(RECOMMENDED),      // не проверяемо: подтверждение пользователя
       DO_NOT_DISTURB(INFO),             // будильники проходят текущий фильтр DND
       ALARM_VOLUME(INFO),               // STREAM_ALARM > 0 (пересмотреть в M4, FR-SND-7)
       SCHEDULING(CRITICAL),             // нет будильников со scheduleFailed (ADR-015)
   }
   enum class Severity { CRITICAL, RECOMMENDED, INFO }
   enum class HealthStatus { OK, PROBLEM, UNCONFIRMED }   // UNCONFIRMED — только OEM_BACKGROUND без «Я сделал»

   /** Сырые значения платформы; заполняет :core:permissions. */
   data class PermissionSnapshot(
       val notificationsEnabled: Boolean, val ringingChannelEnabled: Boolean,
       val exactAlarms: Boolean, val fullScreenIntent: Boolean, val overlay: Boolean,
       val ignoringBatteryOptimizations: Boolean, val backgroundRestricted: Boolean,
       val alarmsAllowedByDnd: Boolean, val alarmVolumeMuted: Boolean,
   )
   data class HealthCheck(val item: HealthItem, val status: HealthStatus)
   data class HealthReport(val checks: List<HealthCheck>, val unscheduledAlarms: Int) {
       /** Баннер FR-LIST-5: хоть один CRITICAL в PROBLEM. */
       val needsAttention: Boolean
   }
   /** Чистая сборка отчёта — единственное место правил «что считать проблемой». */
   fun healthReport(snapshot: PermissionSnapshot, setup: SetupState, unscheduledAlarms: Int): HealthReport

   interface PermissionHealthChecker { fun snapshot(): PermissionSnapshot }  // синхронно, дёшево (несколько binder-вызовов)
   ```
   `SetupState` (флаг «онбординг пройден», подтверждение OEM) — ADR-013. Число незапланированных будильников — из `observeAlarmsWithRuntime()` (ADR-015).
2. **Severity — отклонение от PRD §3.7 (на утверждение):** баннер показывается только для пунктов, без которых будильник на стоковом Android не звонит или звонит без экрана: уведомления (+канал), точные будильники, FSI, фоновое ограничение, планирование. Overlay и оптимизация батареи — RECOMMENDED: ⚠️ на экране здоровья и обязательный показ в онбординге, но без постоянного баннера (иначе пользователь, осознанно отказавший в overlay, живёт с вечным баннером и перестаёт его замечать). Новый пункт BACKGROUND_RESTRICTION — критичный. Правка PRD §3.7 — в T-docs.
3. **`:core:permissions`** (новый Android-модуль, `balarm.android.library` + `balarm.android.compose`, Hilt-биндинг): `AndroidPermissionHealthChecker` (реализация п. 1 через `NotificationManagerCompat`, `NotificationManager.getNotificationChannel(alarm_ringing)`, `AlarmManager`, `Settings.canDrawOverlays`, `PowerManager`, `ActivityManager.isBackgroundRestricted`, `NotificationManager.currentInterruptionFilter` + политика категории «будильники», `AudioManager.getStreamVolume(STREAM_ALARM)`); перед чтением канала вызывает `AlarmNotificationChannels.ensureCreated` (зависимость `:core:permissions → :core:alarm`; обратной нет). Модуль не directBootAware и в цепочке звонка не участвует.
4. **«Исправить» — без `Intent` во ViewModel.** ViewModel отдаёт эффект `Fix(item: HealthItem)`; экран вызывает помощник из `:core:permissions`:
   ```kotlin
   @Composable fun rememberHealthFixLauncher(onReturned: () -> Unit): (HealthItem) -> Unit
   ```
   Внутри: `NOTIFICATIONS` — runtime-запрос `POST_NOTIFICATIONS`, а если отказ «навсегда» (после отказа `shouldShowRequestPermissionRationale == false`) или разрешение есть, но выключен канал — `ACTION_APP_NOTIFICATION_SETTINGS` / `ACTION_CHANNEL_NOTIFICATION_SETTINGS`; `FULL_SCREEN_INTENT` — `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (`package:` URI); `OVERLAY` — `ACTION_MANAGE_OVERLAY_PERMISSION`; `BATTERY_OPTIMIZATION` — `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (запасной — `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`); `BACKGROUND_RESTRICTION`, `OEM_BACKGROUND` — `ACTION_APPLICATION_DETAILS_SETTINGS` (+ ссылка dontkillmyapp.com для OEM); `DO_NOT_DISTURB` — `ACTION_ZEN_MODE_PRIORITY_SETTINGS`/`ACTION_SOUND_SETTINGS`; `ALARM_VOLUME` — `ACTION_SOUND_SETTINGS`; `SCHEDULING` — не интент, ViewModel вызывает `engine.rescheduleAll(USER_RETRY)`. Каждый интент проверяется `resolveActivity`, нет обработчика → `ACTION_APPLICATION_DETAILS_SETTINGS`. `ActivityNotFoundException` ловится. Тексты пунктов (заголовок, «зачем», RU+EN) — ресурсы `:core:permissions` (общие для онбординга и экрана здоровья).
5. **Пересчёт** — на каждом `ON_RESUME` экранов списка, здоровья, настроек и онбординга (`LifecycleResumeEffect` → событие `Resumed` → `checker.snapshot()`); возврат из системных настроек — это тот же `ON_RESUME`. Ни ресиверов, ни опроса в фоне (NFR-9). Платформа не шлёт broadcast на смену FSI/overlay/канала — опрос на resume единственный путь.
6. **Баннер FR-LIST-5:** `AlarmListUiState.healthWarning: Boolean = report.needsAttention`; `AlarmListRoute(onAddAlarm, onOpenAlarm, onOpenHealth, modifier)`; компонент `HealthBanner(text, actionLabel, onClick)` в `:core:designsystem`. Показывается независимо от наличия будильников.
7. **Overlay-путь звонка (FR-REL-5) — в M3:** `SYSTEM_ALERT_WINDOW` в манифест `:core:alarm` и `config/permissions-allowlist.txt`; `RingingService` после публикации FSI-уведомления при `Settings.canDrawOverlays` вызывает `startActivity(uiIntents.ringingScreen() + FLAG_ACTIVITY_NEW_TASK)` один раз на звонок (очередь — на каждый следующий). `RingingActivity` — `singleTask`, двойной старт (FSI + startActivity) даёт `onNewIntent`. Исключения старта логируются и не мешают звуку. Возврат экрана наверх через ≤ 3 с после Home (остальное FR-RING-5) — M5 (R17). `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — в манифест `:core:permissions` и allowlist.
8. **Тестовый будильник 1 мин** — `TestAlarmRunner.schedule(AlarmDefaults.testAlarm(now), TestAlarmRunner.HEALTH_DELAY)` (ADR-010 без изменений по сути). Перенос M2: `TestAlarmRunner.schedule` сам ловит любое исключение, кроме отмены (возвращает прежний снимок, пишет `ScheduleFailed(TEST)`, отдаёт `null`) — одно место для редактора и экрана здоровья.
9. **Флагов нет** (FR-FLAG-4): онбординг, здоровье, баннер — ядро надёжности.

## Alternatives considered
* **Severity строго по PRD (overlay и батарея критичны → баннер).** Проще объяснить, но вечный баннер у отказавшихся. Оставлено как вариант на выбор пользователя.
* **Интерфейс чекера в `:core:permissions`, ViewModel зависит от модуля напрямую.** ViewModel трёх экранов тогда тестируются только через Robolectric. Интерфейс в домене оправдан тремя потребителями. Отклонено.
* **`Intent` в эффектах ViewModel.** Нарушает «ViewModel без Android». Отклонено.
* **Мониторинг через ресиверы** (`NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED`, `APP_BLOCK_STATE_CHANGED`) — покрывают только уведомления, нужны работающие в фоне ресиверы. Отклонено: resume-опрос достаточен.
* **Не реализовывать overlay-путь, только спросить разрешение** — обманывает пользователя. Отклонено.

## Consequences
* (+) Одна чистая функция правил — табличные unit-тесты на JVM; три экрана используют один отчёт.
* (+) Баннер честен и для отказа планирования (ADR-015).
* (−) Новый модуль и две новые записи в allowlist; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — риск ревью Google Play (PRD §11 вопрос 2); запасной интент без разрешения описан.
* (−) DND: чтение политики «будильники разрешены» на API 35+ (режимы, `AutomaticZenRule`) нужно проверить в задаче на API 37; при сомнении статус OK (INFO не влияет на баннер).
* Проверка: unit — `healthReport` (каждая комбинация → статус, `needsAttention`), ViewModel на фейке чекера; Robolectric — `AndroidPermissionHealthChecker` (`ShadowNotificationManager`, `ShadowPowerManager`, `ShadowSettings.setCanDrawOverlays`, `ShadowAlarmManager`), выбор интентов; эмулятор API 34 и 37 — каждый пункт переключается adb (`pm revoke … POST_NOTIFICATIONS`, `appops set … USE_FULL_SCREEN_INTENT deny`, `appops set … SYSTEM_ALERT_WINDOW allow|deny`, `dumpsys deviceidle whitelist ±`, `appops set … RUN_ANY_IN_BACKGROUND ignore`, `cmd notification set_dnd …`, `media volume --stream 4 --set 0`) → статус на resume и баннер; R10 (overlay), R12.

## Related
ADR-002, ADR-007 §2, §8, §10, ADR-009, ADR-010, ADR-011 §11 Б, ADR-013, ADR-014, ADR-015; PRD §3.7, FR-LIST-5, FR-REL-5, FR-REL-7, FR-RING-1, FR-RING-5, FR-FLAG-4, NFR-9, §7; R10, R11, R12.

## Уточнения по итогам M3
* §1: в `PermissionSnapshot` добавлено вычисляемое `notificationsReady = notificationsEnabled && ringingChannelEnabled`.
* §4: вместо `resolveActivity` — `try/catch ActivityNotFoundException/SecurityException` по цепочке интентов (без `<queries>` на API 30+ `resolveActivity` вернул бы `null` для системных экранов). Порядок: специфичный интент → запасной → App details; канал звонка (`channelOnly`) — `ACTION_CHANNEL_NOTIFICATION_SETTINGS`; DND → `ZEN_MODE_PRIORITY_SETTINGS` → Sound.
* §4: отказ `POST_NOTIFICATIONS` — после **первого** отказа следующее «Разрешить» ведёт в настройки (флаг `notificationsDenied`); если диалога уже нет (`shouldShowRequestPermissionRationale == false`) — настройки сразу. Проще и безопаснее «навсегда».
* §4: на API 37 `ACTION_MANAGE_OVERLAY_PERMISSION` открывает общий список приложений, а не страницу Balarm (поведение ОС; `package:` URI передаётся).
* §7: overlay-старт экрана звонка проверен на API 37 (T-test, R10): работает при выданном `SYSTEM_ALERT_WINDOW` и выключенном FSI; гарантий платформа не даёт, основной путь — full-screen intent.
* `ensureCreated` внутри `snapshot()` обёрнут в `runCatching`.
