# ADR-019: Миссия в звонке: хост в RingingActivity, приглушение и таймер бездействия в сервисе, запрет обхода

## Status
Proposed (2026-10-10, план M5). Расширяет ADR-007 (сессия звонка) и ADR-017 §1 (обобщение `setMuted`, как там и было запланировано); громкость потока (`AlarmVolumeController`, владельцы RINGING/PREVIEW) не меняется.

## Context
* FR-RING-3 («Отключить» при миссии → экран миссии), FR-RING-4 (громкость и питание не отключают), FR-RING-5 (Home/Recents → звук идёт, экран возвращается ≤ 3 с при overlay), FR-MIS-1 (прогресс и таймер бездействия), FR-MIS-2 (30 % на время миссии; бездействие > 15 с → 100 % и сброс текущей задачи), FR-MIS-3 (Back → экран звонка, звук громкий), NFR-5/R20 (падение посреди миссии), R16, R17.
* Звук живёт в `RingingService` (ADR-007); UI может исчезнуть (Home, падение, выключение экрана) — состояние «приглушено» не должно зависеть от того, жив ли экран.
* Android 17 background audio hardening (ADR-017 Context) касается **громкости потока** (`setStreamVolume` и т. п.); усиление собственного `MediaPlayer.setVolume` им не ограничено.
* BAL: `SYSTEM_ALERT_WINDOW` остаётся исключением для старта Activity из фона (developer.android.com/guide/components/activities/background-starts); требование «видимое overlay-окно» с Android 15 относится к старту **FGS**, не Activity.
* Кнопку питания и Home приложение перехватить не может.

## Decision
1. **Хост — внутри `RingingActivity`** (не отдельная Activity): одна точка `showWhenLocked`/`singleTask`/поглощения клавиш. `RingingViewModel` получает фазы `RINGING → MISSION → FINISHED`; `MissionHost` (`:feature:ringing`) = `MissionFrame` (ADR-018 §7) + `ResolvedMission.Content` + `MissionSession` поверх `RingingController`. Seed — `SecureRandom` при входе в миссию, хранится в ViewModel (переживает поворот), **не** в `SavedStateHandle`. Системные бары на экране миссии видимы, как на звонке (FR-RING-1).
2. **Миссия звонка определяет сервис**: `RingingState.Ringing.mission = alarm.effectiveMission(catalog)` (ADR-018 §2). `degraded`-звонок (БД недоступна) — без миссии («в сомнении — звони» важнее G2). Тест (ADR-010) — с миссией из снимка черновика.
3. **Контракт контроллера** (`:core:domain`):
   ```kotlin
   interface RingingController {
       val state: StateFlow<RingingState>
       fun dismiss()            // только для звонка без миссии; при миссии сервис отклоняет (MISSION_BYPASS_REJECTED)
       fun snooze()             // как раньше: «Отложить» миссию не требует (FR-RING-2)
       fun startMission()       // «Отключить» при миссии: приглушить, запустить таймер бездействия
       fun missionInteraction() // любое касание в миссии
       fun leaveMission()       // Back из миссии: сразу громко (FR-MIS-3)
       fun completeMission()    // миссия пройдена → dismiss(USER) + MISSION_COMPLETED
       fun screenVisible(visible: Boolean) // FR-RING-5
   }
   data class MissionRun(val idleDeadline: Instant?, val idleResets: Int)   // idleDeadline == null — бездействие истекло, громко
   // RingingState.Ringing: + val mission: MissionSpec? = null, + val missionRun: MissionRun? = null
   ```
   `dismiss`/`snooze` — интентами (как в ADR-007 §3, их шлёт и уведомление); миссионные команды — частые, поэтому идут **внутрипроцессным** `Channel` из `RingingControllerImpl`, который сервис собирает на главном потоке (без `startService` на каждое нажатие).
4. **Приглушение и таймер бездействия — аренда в сервисе.** `startMission`/`missionInteraction` → `sound.attenuate(MISSION, MissionPolicy.DUCK_GAIN)`, вибрация на паузу, перезапуск таймера `MissionPolicy.IDLE_TIMEOUT`; публикуется `MissionRun(idleDeadline = now + timeout)`. Истёк → `attenuate(MISSION, null)` (сразу 100 %, без нарастания), вибрация по настройке, `MissionRun(null, idleResets + 1)`, событие `MISSION_IDLE`. Хост видит рост `idleResets` → `MissionSession.resetSignal`; полоска бездействия считается из `idleDeadline`. Следующее касание снова приглушает. `leaveMission`, `screenVisible(false)`, `snooze`, конец звонка → аренда снимается сразу. Падение/исчезновение UI → аренда истекает сама: «тихо навсегда» невозможно. `MissionPolicy` (`:core:domain`): `IDLE_TIMEOUT = 15 с`, `DUCK_GAIN = 0.3f`; настройки 10–60 с и уровня — глобальные настройки (§3.9), когда появится экран.
5. **Плеер: обобщение `setMuted`** (ADR-017 §1):
   ```kotlin
   enum class Attenuation { CALL, MISSION }
   fun attenuate(source: Attenuation, gain: Float?)   // null — снять; итог = нарастание × min(активных); CALL = 0f
   ```
   Снятие `CALL` — возврат с нарастанием 15 с (как сейчас); снятие `MISSION` — сразу. Резервный `ToneGenerator` не приглушается (громче — безопасная сторона). Громкость потока, `VolumeOwner` и hardening не затрагиваются: нового владельца/состояния в `AlarmVolumeController` нет.
6. **Запрет обхода.**
   * Уведомление: при `mission != null` кнопки «Отключить» нет (ADR-007 §2.1); `ACTION_DISMISS` для звонка с миссией сервис отклоняет. Отключение — только `completeMission()` текущего id.
   * Back: на звонке поглощается (как в M1); в миссии → `leaveMission()` и возврат на экран звонка.
   * Home/Recents (FR-RING-5, R17): `RingingActivity.onStop` при `isInteractive && !isChangingConfigurations` → `screenVisible(false)`; сервис через `SCREEN_RETURN_DELAY = 1 с`, если экран всё ещё скрыт и звонок идёт, — `startRingingScreen()` (только при `canDrawOverlays`); без overlay — ongoing-уведомление (heads-up) ведёт на экран. Activity не уничтожается — фаза миссии и решённые задачи сохраняются (бездействие уже вернуло громкость).
   * Кнопки громкости: при фокусе — поглощаются Activity (M1). Без фокуса (экран выключен, другое окно) клавиши меняют `STREAM_ALARM` — **`VolumeGuard`** в сервисе для звонка с миссией: раз в 1 с читает индекс; ниже выставленного (`applied`) → выставить обратно (сервис в foreground, исключение exact alarm + `USAGE_ALARM`), событие `VOLUME_GUARD`. Повышение пользователем не трогаем. Только на время звонка.
   * Питание: выключение экрана звонок не останавливает; при включении экран звонка — верхний и `showWhenLocked`. Возврат по `screenVisible(false)` при `!isInteractive` не делается.
7. **Падение посреди миссии (NFR-5, R20).** Прогресс миссии **теряется намеренно**: crash re-arm (ADR-007 §7) возвращает звонок, экран открывается в фазе `RINGING`, миссия — заново с новым seed. Восстанавливать прогресс опасно: воспроизведение падения → второе падение < 60 с → система снимает alarms. Поэтому **предохранитель**: если в момент падения шла миссия (`@Volatile`-снимок, как `rearmIds`), RESUME ставится с признаком `skipMission` (extra интента, `ScheduleRequest.skipMission`); такой звонок — без миссии (кнопка «Отключить» сразу), событие `MISSION_SKIPPED_AFTER_CRASH`. Один раз на падение.
8. **Автостоп** (FR-RING-6) — без изменений: 30 мин от начала звонка, миссия его не продлевает (пересмотреть в M7 вместе с настройкой).
9. **События лога:** `MISSION_STARTED`, `MISSION_IDLE resets=N`, `MISSION_LEFT`, `MISSION_COMPLETED`, `MISSION_BYPASS_REJECTED`, `MISSION_SKIPPED_AFTER_CRASH`, `VOLUME_GUARD`, `SCREEN_RETURNED` — для скилла `verify-alarm-reliability` (R16, R17, R20).

## Alternatives considered
* **Таймер бездействия только в UI (хост)** — проще, но если экран исчез/упал во время приглушения, звук остаётся 30 % до автостопа: нарушает G2. Отклонено: источник истины — аренда в сервисе.
* **Приглушение через индекс `STREAM_ALARM`** (новый `VolumeOwner.MISSION`) — грубые шаги (~7 на Pixel), каждый вызов под hardening Android 17, ломает сравнение `applied` при восстановлении. Отклонено: усиление плеера.
* **Отдельная `MissionActivity`** — чище разделение, но второе окно поверх блокировки, второй `singleTask`/поглощение клавиш и гонки при возврате. Отклонено.
* **Сохранять прогресс миссии в `SavedStateHandle`/БД** — меньше раздражения после падения, но риск crash-loop и снятия alarms (R20) и путь «упади — пройди проще». Отклонено.
* **Не отключать миссию после падения (без предохранителя)** — строже для G2, но второе падение в миссии за < 60 с снимает все будильники. Отклонено (вынесено в открытые вопросы M5).
* **Отдельный процесс для сервиса** (ADR-007 альтернатива) — по-прежнему дорого; предохранитель п. 7 закрывает главный риск миссий. Отклонено.

## Consequences
* (+) Отключить будильник с миссией можно только пройдя её; бездействие, Back, Home, исчезновение UI возвращают 100 % без участия экрана.
* (+) Android 17 hardening не затронут: приглушение — усиление собственного плеера.
* (−) `VolumeGuard` опрашивает громкость раз в секунду во время звонка (только в сессии, NFR-9 не нарушает).
* (−) После падения в миссии будильник отключается без миссии (осознанная уступка ради R20).
* (−) Возврат после Home зависит от overlay-разрешения и задержек переключения приложений у OEM — проверка R17 на API 34/37 и реальном устройстве.
* Проверка: unit/Robolectric — аренда (виртуальное время: старт → 30 %, 15 с → 100 % и `idleResets+1`, касание → снова 30 %, `leaveMission`), отклонение `ACTION_DISMISS` при миссии, `attenuate` (CALL + MISSION, снятие), `VolumeGuard`, предохранитель после падения; Compose UI — фазы RINGING/MISSION, Back из миссии; эмулятор — R16, R17 (Home, Recents, Back, громкость при выключенном экране), R20 (debug `CRASH` в миссии → звонок без миссии), R14 (звонок во время миссии), R19.

## Related
ADR-002, ADR-007, ADR-008, ADR-010, ADR-017, ADR-018, ADR-020; FR-RING-1…6, FR-RING-8, FR-MIS-1…3, FR-REL-5, NFR-5, NFR-9; R14, R16, R17, R19, R20.

## Уточнения по итогам M5 (2026-10-10)
* Home/Recents-скрытие (`missionScreenHidden()`) **завершает сессию миссии**: звук сразу громкий, экран возвращается на звонок, после «Отключить» миссия начинается заново с новым seed. Решённые задачи не сохраняются (замена §6) — безопаснее для обхода.
* Нет `idleDeadline` в `MissionRun`: полоска бездействия считается на UI от последнего касания, источник истины по таймеру — сервис (15 с, `MissionPolicy.IDLE_TIMEOUT`). Вместо `screenVisible(Boolean)` — `missionScreenHidden()`. Команды миссии идут интентами `ACTION_MISSION_*`, а не внутрипроцессным каналом.
* Приглушение — `AlarmSoundPlayer.setDuck(gain)` (множитель усиления плеера, 30 %), независимо от `setMuted` (FR-RING-8); режим резервного тона приглушение не применяет (звучит громче — безопасная сторона).
* Возврат экрана после Home: `RingingService.scheduleScreenReturn` (через 1 с, при overlay-разрешении, не во время разговора и не при выключенном экране).
* Известные пробелы (в бэклоге): Recents не возвращает экран (`RingingActivity` лишь paused); выключение экрана посреди миссии оставляет 30 % до idle ≤ 15 с; `VolumeGuard`.
* Риск: у звонка с миссией нет кнопки «Отключить» в уведомлении; если нет overlay и экран звонка не открылся (нет уведомлений/FSI), отключить можно только открыв приложение. Предупреждения — онбординг и экран здоровья (ADR-012/013).
