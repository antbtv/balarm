# ADR-011: Контракт UI ↔ движок: список, «следующий будильник», сохранение, формат времени

## Status
Proposed (2026-10-04, этап M2). Уточняет ADR-006 §5–§8 (семантика `save`/`setEnabled` для UI); модель планирования не меняется.

## Context
* FR-LIST-1…4, FR-EDIT-1/2/4/7/10/11, AC FR-LIST (тумблер немедленно отменяет alarm), AC FR-EDIT (Back → диалог). M2 — первый этап, где расписание меняет пользователь, а не debug-команда.
* Сейчас (M1): `AlarmRepository.observeAlarms(): Flow<List<Alarm>>` без runtime; `AlarmEngine.save(alarm): AlarmId` и `setEnabled` создают **чистый** runtime (`AlarmRuntimeState(id)`) — теряются отложенный звонок и `lastFiredAt` (защита от повторного звонка после перевода часов назад, ADR-006 §6).
* Разовый будильник после срабатывания выключен (`enabled = false`), но его snooze может быть ещё впереди (`nextTriggerKind = SNOOZE`) — тумблер «выкл», а будильник зазвонит.
* Форматирование времени скопировано трижды (`AlarmNotifications`, `RingingScreen`, `BalarmApp`) с одинаковой логикой «ICU-скелет `Hm`/`hm` + запасной `ofLocalizedTime`». В M2 добавляются ещё два потребителя (список, редактор) и относительное время «через 7 ч 12 мин» (шапка FR-LIST-2 и тост FR-EDIT-11).
* Лимит метки — 40 символов; сейчас `String.length` (UTF-16): эмодзи считаются за 2 (замечание ревью M1).
* NFR-9: никаких постоянных сервисов/воркеров; UI может тикать только пока виден.

## Decision
1. **Чтение для UI** — репозиторий, без use case-обёрток (у них не было бы логики):
   ```kotlin
   interface AlarmRepository {
       fun observeAlarmsWithRuntime(): Flow<List<AlarmWithRuntime>>   // заменяет observeAlarms(); ORDER BY hour, minute, id
       ...
   }
   ```
   Room: `@Transaction` + `@Relation` (или `LEFT JOIN`) по `alarm` и `alarm_runtime` — Flow инвалидируется при изменении любой из таблиц. Сортировка — по времени суток (FR-LIST-1), не по ближайшему срабатыванию: список не «прыгает» при переключении тумблера.
2. **Источник истины «когда зазвонит» — `runtime.nextTriggerAt`** (то, что реально отдано `setAlarmClock`), а не пересчёт `NextTriggerCalculator` в UI. Чистые функции в `:core:domain`:
   ```kotlin
   /** Ближайшее будущее срабатывание будильника (REGULAR/SNOOZE/CATCH_UP) или null. */
   fun AlarmWithRuntime.upcomingTrigger(now: Instant): Instant?
   /** Активен = включён или есть ожидающий snooze/догон (тумблер показывает «вкл»). */
   fun AlarmWithRuntime.isActive(now: Instant): Boolean
   /** Шапка FR-LIST-2: минимум upcomingTrigger по всем; null → «Нет активных будильников». */
   fun List<AlarmWithRuntime>.nextTrigger(now: Instant): Instant?
   ```
   Момент в прошлом игнорируется (пропуск ещё не перепланирован). После смены зоны/времени `RescheduleReceiver` → `rescheduleAll` обновляет runtime → Flow → шапка пересчитывается без собственных ресиверов UI.
3. **Тик UI.** «Через X» пересчитывается по `minuteTicks(clock)` (перенос из `RingingViewModel` в `:core:domain`, общий для двух потребителей), только пока экран в `STARTED` (`stateIn(WhileSubscribed(5 s))` + `collectAsStateWithLifecycle`). Ни ресиверов `TIME_TICK`, ни воркеров.
4. **Относительное время** — чистая функция `:core:domain`:
   ```kotlin
   data class TimeUntil(val days: Int, val hours: Int, val minutes: Int)
   /** Округление ВВЕРХ до минуты: 06:59:30 → 07:00 = «через 1 мин»; ноль не бывает (at > now). */
   fun timeUntil(now: Instant, at: Instant): TimeUntil
   ```
   Отображение — нулевые части опускаются: «12 мин», «7 ч», «7 ч 12 мин», «2 д 3 ч 5 мин». Текст — ICU `android.icu.text.MeasureFormat` (`FormatWidth.SHORT` на экране, `WIDE` для TalkBack: «7 часов 12 минут» с правильными падежами RU) — без собственных plurals; обёртки «Следующий будильник через %1$s» / «Будильник зазвонит через %1$s» — строковые ресурсы RU/EN.
5. **Мутации — только `AlarmEngine`** (ADR-006 §8); ViewModel зависит от `AlarmEngine` + `AlarmRepository` (+ `TestAlarmRunner`, ADR-010). Изменения движка:
   ```kotlin
   data class ScheduleResult(val id: AlarmId, val nextTriggerAt: Instant?, val scheduled: Boolean)
   suspend fun save(alarm: Alarm): ScheduleResult              // было: AlarmId
   suspend fun setEnabled(id: AlarmId, enabled: Boolean): ScheduleResult?   // null — будильника нет
   ```
   * `scheduled = false` → тост «Не удалось запланировать будильник» (детали — экран здоровья, M3), а не «зазвонит через».
   * **«Сохранить» всегда включает будильник** (`enabled = true`), как в системных часах и Alarmy: пользователь редактирует, чтобы будильник звонил.
   * **`save` сохраняет `lastFiredAt`** и **ожидающий SNOOZE/CATCH_UP**, если он в будущем и раньше нового обычного срабатывания (тогда `snoozeCount` тоже сохраняется); иначе — обычный план, `snoozeCount = 0`. Пример: отложен до 07:05, пользователь поменял метку → звонок в 07:05 остаётся («в сомнении — звони»); поменял время на 07:03 сегодня → 07:03.
   * **`setEnabled(false)` отменяет всё**, включая ожидающий snooze (AC FR-LIST: `dumpsys alarm` без нашего интента). `setEnabled(true)` — обычный план, `lastFiredAt` сохраняется.
   * `delete` — без изменений (БД → `cancel`).
   * **Звонящий будильник.** Сессия звонка держит снимок `FireDecision.Ring` (ADR-007): редактирование/удаление во время звонка звук не меняет и не останавливает; «Отключить» после редактирования планирует по новым настройкам, после удаления — `dismiss` ничего не пишет; RESUME удалённого → `Skip(DELETED)`. Отдельной блокировки UI не нужно: `MainActivity.onStart` и так уводит на экран звонка.
   * Повторное нажатие «Сохранить» гасится в ViewModel (`saving`); операции движка `NonCancellable` — уход с экрана посреди сохранения его не прерывает.
6. **Общий форматтер — модуль `:core:format`** (Android library; Compose runtime только для `remember*`-хелперов). Содержит: `ClockFormat` (время/дата: ICU-скелет `Hm`/`hm`/`EEEEdMMMM` + запасной `ofLocalizedTime/Date`, `is24Hour` из `DateFormat.is24HourFormat`, AM/PM отдельно для карточки), `formatTimeUntil(TimeUntil, locale, wide)`, `WeekdayFormat` (порядок дней от первого дня недели и короткие имена). Первый день недели — `androidx.core.text.util.LocalePreferences.getFirstDayOfWeek()` (учитывает «Региональные настройки» Android 14+; глобальная настройка PRD §3.9 — вместе с экраном настроек). `@Composable rememberClockFormat()` перечитывает `is24Hour` на каждом `ON_START` (смена 12/24 в системе не меняет конфигурацию). Потребители: `:core:alarm` (уведомления), `:feature:ringing`, `:feature:alarmlist`, `:feature:alarmedit`. Три копии удаляются.
7. **Метка — в code points.** `Alarm.init`: `label.codePointCount(0, label.length) <= MAX_LABEL_LENGTH`; утилита `:core:model` `fun String.takeCodePoints(n: Int): String` (не режет суррогатную пару) — для поля ввода, маппера Room и debug-команды. Перевод строки в метке запрещён (однострочное поле), пробелы по краям обрезаются при сохранении. Графемы (ZWJ-эмодзи) могут занимать несколько code points — принято.
8. **Дефолты нового будильника** — `AlarmDefaults.newAlarm(now: LocalTime)` в `:core:domain`: время — следующий целый час, дни — пусто (разовый), метка пустая, `vibrate = true`, `snooze = SnoozeSettings.DEFAULT`, `enabled = true`. Варианты интервала/лимита FR-EDIT-7 — константы `SnoozeSettings.INTERVAL_OPTIONS` (1,3,5,10,15,20,30 мин) и `LIMIT_OPTIONS` (1,2,3,5,10,∞); значение из БД вне списка показывается как есть.
9. **Флаг `feature.snooze` = false** в редакторе: секция snooze скрыта, при сохранении `alarm.snooze` не меняется (данные не теряются, FR-FLAG-3), новый будильник получает `DEFAULT`; на звонке кнопки нет (уже в M1: `AlarmEngine.canSnooze`).
10. **`AlarmRuntimeState.hasFiredFor(at: Instant)`** — член модели вместо трёх копий `lastFiredAt?.let { !it.isBefore(x) } == true` в движке.

## Alternatives considered
* **Пересчитывать «следующий» в UI через `NextTriggerCalculator`** — не видит snooze/CATCH_UP и правило «после перевода часов назад», дублирует логику движка и может разойтись с `AlarmManager`. Отклонено.
* **Use case-классы `ObserveAlarms`/`SaveAlarm`/`ToggleAlarm`/`DeleteAlarm`** — пересказ методов движка/репозитория без логики. Отклонено; логика списка — чистые функции п. 2.
* **`save` сбрасывает snooze** (как в M1) — проще, но «посмотрел и нажал Сохранить» во время snooze = проспал. Отклонено.
* **Форматтер в `:core:designsystem`** — тянет Compose UI/material3 в `:core:alarm` и смешивает слой UI-кита с инфраструктурой уведомлений. **Форматтер в `:core:alarm`** — `:feature:ringing` не должен зависеть от `:core:alarm` (ADR-007 §9). Отклонено.
* **Свои `<plurals>` для «7 часов 12 минут»** — ICU `MeasureFormat` даёт то же для RU/EN и любой будущей локали без ресурсов. Отклонено.
* **`WeekFields.of(locale).firstDayOfWeek`** — не учитывает пользовательский «первый день недели» Android 14. Отклонено.
* **Ресивер `TIME_TICK`/`TIMEZONE_CHANGED` в UI** — лишний: runtime обновляет `RescheduleReceiver`, остальное — минутный тик видимого экрана. Отклонено.

## Consequences
* (+) Шапка и карточки показывают ровно то, что стоит в `AlarmManager`, включая snooze; тумблер разового отложенного будильника честно «вкл».
* (+) Редактирование не теряет отложенный звонок и защиту от повторного звонка.
* (+) Одна реализация форматирования времени на 4 потребителя.
* (−) Меняется сигнатура `AlarmEngine.save` (потребители: debug-команды, тесты).
* (−) Новый модуль `:core:format`.
* (−) Вывод ICU может отличаться между версиями Android («7 ч 12 мин» vs «7 ч, 12 мин») — тесты сравнивают с тем же `MeasureFormat`, а не с литералом; литералы — только для ru/en на Robolectric SDK 37.
* Проверка: unit — `upcomingTrigger/isActive/nextTrigger` (snooze у выключенного разового, прошедший момент, пусто), `timeUntil` (границы 59 с, 60 с, 23:59:30, 7 д), движок (`save` сохраняет snooze/`lastFiredAt`, `setEnabled(false)` отменяет snooze, редактирование и удаление во время звонка); Robolectric — `observeAlarmsWithRuntime` эмитит при изменении runtime; эмулятор — R1, R5 (шапка после смены зоны), AC FR-LIST через `dumpsys alarm`.

## Related
ADR-004, ADR-006 §5–§8, ADR-007 §9, ADR-009, ADR-010; FR-LIST-1…4, FR-EDIT-1, 2, 4, 7, 10, 11, FR-FLAG-3, FR-REL-4, NFR-7, NFR-9; R1, R5, R6.
