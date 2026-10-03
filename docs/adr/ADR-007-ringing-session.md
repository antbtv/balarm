# ADR-007: Сессия звонка: RingingService, уведомление, очередь, автостоп, восстановление после падения

## Status
Accepted (2026-10-03, реализовано и проверено в M1). Предложено 2026-09-29. Детализирует ADR-002 (тип FGS не меняется).

## Context
* FR-RING-1/5/6/7, FR-REL-5, NFR-5. Звук — в FGS `RingingService` (ADR-002). Приложение однопроцессное: необработанное исключение в UI убивает процесс **вместе с сервисом** — формулировка NFR-5 «звук в сервисе, отдельно от UI» сама по себе падение не переживает.
* Старт FGS из фона разрешён исключением «exact alarm» на короткое окно после доставки alarm → FGS нужно стартовать сразу в ресивере, до любого I/O.
* FSI: на API 33+ без `POST_NOTIFICATIONS` уведомления (в т.ч. FSI) не показываются, но FGS запускается и работает (уведомление видно только в FGS Task Manager). На API 34+ `USE_FULL_SCREEN_INTENT` может быть отозвано → система показывает heads-up вместо полноэкранного экрана. При разблокированном используемом устройстве FSI тоже превращается в heads-up (поверх — overlay, M3).
* Android 12+: уведомление FGS может показываться с задержкой до 10 с, если не запрошено немедленное.
* Корутинный `delay` на `Handler` не идёт во время глубокого сна CPU.
* `:core:alarm` не должен зависеть от `:feature:*` (PRD §6.2), но строит `PendingIntent` на экран звонка и на главный экран.

## Decision
1. **Цепочка.** `setAlarmClock` → `AlarmReceiver` (directBootAware, `exported=false`) → берёт статический partial WakeLock (таймаут 60 с) → **сразу** `ContextCompat.startForegroundService(RingingService, ACTION_RING, alarmId, scheduledFor, kind)`; никакого I/O до этого. `ForegroundServiceStartNotAllowedException` → fallback-уведомление (п. 6).
2. **`RingingService.onStartCommand`** (directBootAware, `foregroundServiceType="systemExempted"`, `exported=false`):
   1. Немедленно `startForeground` с базовым уведомлением (`startForeground(..., FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)`; minSdk 34). Уведомление: канал `alarm_ringing` (IMPORTANCE_HIGH, без звука — звук играет сервис), `CATEGORY_ALARM`, ongoing, `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`, `fullScreenIntent` и `contentIntent` → `RingingActivity`. **Не** `setSilent`: «тихое» уведомление система не считает прерывающим и не запускает FSI (найдено на эмуляторе в M1). Кнопки «Отложить»/«Отключить» есть, но обе опциональны: с M5 у будильника с миссией «Отключить» в уведомлении нет (иначе обход миссии).
   2. Собственный partial WakeLock на всю сессию (таймаут = автостоп + 1 мин), освобождение WakeLock ресивера; освобождение во всех путях (`onDestroy`, `finally`).
   3. `AlarmEngine.onFired(...)` (ADR-006 п. 6) → `Ring` → `AlarmSoundPlayer.start`, `AlarmVibrator.start` (если `vibrate`), обновить уведомление (метка), `RingingController.state = Ringing`, событие `RINGING_STARTED`. Исключение в движке → звонить с дефолтами.
3. **Команды** — только через интенты сервиса: `ACTION_DISMISS`, `ACTION_SNOOZE` (от `RingingController`, debug-ресивера). `RingingController` (интерфейс в `:core:domain`, реализация в `:core:alarm`) публикует `StateFlow<RingingState>`, который пишет сервис; UI — только читатель + отправитель команд.
4. **Очередь FR-RING-7 — в M1.** Сервис держит `ArrayDeque` ожидающих срабатываний; второй `ACTION_RING` во время звонка → в очередь (`QUEUED`), после dismiss/snooze текущего — следующий. Один экран звонка. Очередь живёт в памяти (процесс удерживает FGS).
5. **Автостоп FR-RING-6** — таймер в сервисе (CPU удерживается WakeLock'ом п. 2.2), в M1 — константа 30 мин (глобальная настройка — вместе с экраном настроек). По таймауту: `dismiss(AUTO_STOP)`, уведомление «Пропущенный будильник» (канал `alarm_missed`), `AUTO_STOPPED`.
6. **Fallback без FGS** (ADR-002 §6): если `startForegroundService`/`startForeground` бросил исключение — уведомление в канале `alarm_fallback` (IMPORTANCE_HIGH, звук — встроенный тон из `res/raw`, `AudioAttributes.USAGE_ALARM`, `FLAG_INSISTENT`, FSI). Звук играет system_server, поэтому ограничения фонового аудио процесса не мешают.
7. **Восстановление после падения процесса.** На время сессии сервис ставит `Thread.setDefaultUncaughtExceptionHandler`: синхронно `setAlarmClock(now + 3 с, тот же PendingIntent, kind = RESUME)` и передаёт исключение предыдущему обработчику. Процесс умирает → через ~3 с alarm → новый процесс → звонок и экран возвращаются. `RESUME` не меняет runtime (не считается новым срабатыванием и не тратит snooze). Снимается при штатном завершении сессии.
8. **Разрешение уведомлений в M1.** `POST_NOTIFICATIONS` объявлено; `MainActivity` запрашивает его при запуске на API 33+ (временное решение до онбординга M3). Без разрешения: звук и вибрация есть, экрана нет; остановка — автостоп или debug-команда. Тестовые прогоны: `adb install -g` / `adb shell pm grant com.antbtv.balarm android.permission.POST_NOTIFICATIONS`.
9. **Развязка с UI-модулями.** `:core:alarm` объявляет `interface AlarmUiIntents { fun ringingScreen(): Intent; fun alarmList(): Intent }`; реализация в `:app` (знает классы Activity). `RingingActivity` живёт в `:feature:ringing` и зависит только от `:core:domain`/`:core:model`/`:core:designsystem`.
10. **`RingingActivity`** (directBootAware, `showWhenLocked`/`turnScreenOn` — методами Activity; `excludeFromRecents`, собственный `taskAffinity`, `launchMode="singleTask"`, `FLAG_KEEP_SCREEN_ON`): Back и кнопки громкости поглощаются (FR-RING-4), при `RingingState.Idle` — `finish()`. Падение Activity не трогает звук (п. 7).

## Alternatives considered
* **Отдельный процесс для сервиса (`android:process=":ringing"`)** — изолирует звук от падений UI по-настоящему, но: Hilt/Application дважды, Room из двух процессов (multi-instance invalidation), IPC вместо `StateFlow`, сложнее Direct Boot-отладка. Отклонено для M1; пересмотреть, если crash-re-arm окажется недостаточным в M5 (миссии).
* **`PendingIntent.getForegroundService` прямо в `setAlarmClock`** — на хоп меньше, но негде сделать fallback-уведомление при отказе старта FGS (ADR-002 §6). Отклонено.
* **Очередь в M2+** — второй `ACTION_RING` без политики перезапускал бы плеер/экран; политика дешёвая и определяет машину состояний сервиса. Отклонено.
* **Action «Отключить» в уведомлении** — удобно, но обходит миссии M5. Отклонено.
* **Тихо игнорировать отсутствие `POST_NOTIFICATIONS` до M3** — DoD M1 не проверяем без экрана. Отклонено.

## Consequences
* (+) DoD M1 проверяем в Doze и Direct Boot; падение UI возвращает звонок за ~3–5 с.
* (−) Пауза звука 3–5 с при падении процесса (приемлемо; тестируется debug-командой `CRASH`).
* (−) Временный запрос разрешения в `MainActivity` удаляется в M3.
* Проверка: Robolectric — `startForeground` с FSI/`CATEGORY_ALARM`, очередь, автостоп (виртуальное время), fallback; эмулятор — R1, R2, R3, R4, R8, R11, R13, R19, debug `CRASH`.

## Related
ADR-001, ADR-002, ADR-006, ADR-008; FR-RING-1…7, FR-REL-5, FR-REL-8, NFR-5, NFR-9; PRD §6.5, §10; R1–R4, R8, R10, R11, R13, R19.

## Уточнения по итогам M1 (2026-10-03)
* **§3–4 Команды и очередь.** Срабатывания обрабатываются по одному (канал), каждое сразу отдаётся движку — в очередь встаёт готовое решение. «Отключить», «Отложить» и автостоп выполняются сразу, вне очереди срабатываний. Сервис останавливается в одной точке и только когда нет звонка, очереди и незаписанных результатов. Ошибка любой команды логируется (`RINGING_COMMAND_FAILED`) и не роняет процесс.
* **§2 Сторож звука.** Если решения движка нет за 2 с (холодная БД после загрузки), звук и вибрация начинаются досрочно (`RINGING_STARTED degraded=true`); решение их подхватывает или гасит.
* **§5 Автостоп** — у каждого звонка очереди свой; сессионный WakeLock продлевается на каждый звонок.
* **§6 Fallback-уведомление** — без кнопки «Отключить»: останавливается смахиванием, нажатием или само через 30 мин (`setTimeoutAfter`).
* **§7 Crash re-arm — ограничения:** `CrashGuard` ставится после первого успешного `startForeground` (падение раньше — Hilt, `onCreate` — не покрыто); обработчик может сработать на любом потоке (читает `@Volatile`-снимок id); повторные падения дают RESUME каждые ~3 с; **два падения за < 60 с — платформа («crashed too many times») останавливает приложение и снимает его alarms** до следующего запуска; RESUME заменяет в системе обычный `PendingIntent` будильника, а в БД остаётся обычный `nextTriggerAt` — `rescheduleAll` в окне ~3 с затрёт RESUME (риск R20; лечится флагом «звонит» в runtime — M3/M5).
* **§8** Запрос `POST_NOTIFICATIONS` — при каждом холодном запуске без разрешения (после двух отказов система перестаёт показывать диалог) — до онбординга M3. Открытое во время звонка приложение ведёт на экран звонка.
* **§10** До первого `Ringing` экран ждёт до 5 с (FSI публикуется раньше решения движка); после звонка `Idle` закрывает экран сразу.
