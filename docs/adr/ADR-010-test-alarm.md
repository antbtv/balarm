# ADR-010: Тестовый звонок («Тест» в редакторе, тестовый будильник на экране здоровья)

## Status
Proposed (2026-10-04, этап M2). Дополняет ADR-006 (идентичность и планирование) и ADR-007 (сессия звонка); их решения не меняются.

## Context
* FR-EDIT-10: кнопка «Тест» — полный сценарий звонка через 5 с для **текущего черновика** (в т.ч. несохранённого нового будильника). FR-REL-7 (M3): «Тестовый будильник через 1 минуту» на экране здоровья — тот же механизм с другой задержкой.
* Инварианты: планирование только через `setAlarmClock` (FR-REL-1); расписание пользователя (таблицы `alarm`, `alarm_runtime`, его `PendingIntent`) тестом не портится; звук — в `RingingService`.
* «Полный сценарий» должен проходить настоящую цепочку: `AlarmReceiver` → FGS-исключение exact alarm → `RingingService` → FSI → `RingingActivity` (а в M4/M5 — мелодия и миссия черновика). Иначе тест не проверяет то, что ломается в жизни (урок M1: FSI не включался из-за `setSilent`).
* Пользователь после «Тест» может заблокировать экран или уйти из приложения — запуск звонка корутиной `delay(5 s)` из UI не переживёт сон CPU, а старт FGS из фона запрещён.
* `PendingIntent` будильника идентифицируется data URI `balarm://alarm/<id>` (ADR-006 §2); `AlarmId` требует `value >= 0`, Room выдаёт id через `AUTOINCREMENT`.

## Decision
1. **Зарезервированный id.** `AlarmId.TEST = AlarmId(Long.MAX_VALUE)` (`:core:model`). Room `AUTOINCREMENT` никогда его не выдаст; URI `balarm://alarm/9223372036854775807` не пересекается с будильниками пользователя → отдельный `PendingIntent`, расписание пользователя не трогается. `AlarmEngine.save/setEnabled/delete` отвергают `TEST` (`require`).
2. **Планирование — `setAlarmClock`**, тем же `AlarmScheduler.schedule(ScheduleRequest(AlarmId.TEST, now + delay, FireKind.REGULAR))`. Повторное «Тест» заменяет предыдущий (тот же URI, `FLAG_UPDATE_CURRENT`). На ~5 с в статус-баре появляется значок будильника — приемлемо.
3. **Снимок черновика** — в памяти процесса: интерфейс `TestAlarmStore` (`:core:domain`), реализация — `@Singleton InMemoryTestAlarmStore`. БД и миграции не нужны, а поля M4/M5 (мелодия, миссии) попадают в тест автоматически — это тот же `Alarm`.
4. **Срабатывание.** `AlarmEngine.onFired(AlarmId.TEST, …)` не идёт в репозиторий и не ждёт `Mutex`: делегирует `TestAlarmRunner.decision()` → `FireDecision.Ring(alarm = снимок ?: дефолтный тестовый, canSnooze = false, snoozesLeft = 0)`. Если процесс умер между «Тест» и срабатыванием (снимка нет) — звонок с настройками по умолчанию («в сомнении — звони»): для теста надёжности это ровно нужное поведение.
5. **Сессия** — обычная (ADR-007): очередь с реальными будильниками (FR-RING-7), автостоп, crash re-arm (RESUME для `TEST` → снимка может не быть → дефолты). `dismiss(TEST)` / автостоп → `TestAlarmRunner.finish()` (очистить снимок), без записи runtime. `snooze(TEST)` → `NotAllowed` (кнопки нет: `canSnooze = false`).
6. **Перепланирование.** `rescheduleAll` тест не видит (его нет в БД) и не трогает; после перезагрузки тест теряется — осознанно.
7. **Контракт:**
   ```kotlin
   // :core:domain
   interface TestAlarmStore { fun put(alarm: Alarm); fun get(): Alarm?; fun clear() }

   class TestAlarmRunner @Inject constructor(
       private val scheduler: AlarmScheduler, private val store: TestAlarmStore,
       private val clock: Clock, private val log: AlarmEventLog,
   ) {
       /** `null` — система отказала (нет права на точные будильники). */
       fun schedule(alarm: Alarm, delay: Duration): Instant?
       fun decision(): FireDecision.Ring
       fun finish()
       companion object { val EDITOR_DELAY: Duration = Duration.ofSeconds(5) }  // M3: Duration.ofMinutes(1)
   }
   ```
   Лог: `TEST_SCHEDULED at=…` (без метки). M3 вызывает `schedule(AlarmDefaults.testAlarm(), Duration.ofMinutes(1))`.
8. **Флаг не нужен:** тестовый звонок — часть цепочки надёжности и экрана здоровья (FR-FLAG-4), а не отдельная пользовательская фича.

## Alternatives considered
* **Временный разовый будильник в таблице `alarm`** (с флагом `is_test` или без) — максимальная точность (тот же путь `onFired` через БД, работает в Direct Boot), но: миграция схемы, фильтрация в списке и «следующем будильнике», «сироты» при гибели процесса до удаления, тратятся id, `rescheduleAll` начинает их видеть. Каждая из этих точек — риск испортить расписание пользователя. Отклонено.
* **Снимок в extras `PendingIntent`** (kotlinx.serialization) — переживает гибель процесса, но требует сериализуемости всех полей `Alarm` (M4/M5 — sealed-конфиги миссий) и пере-сериализации при crash re-arm. Выигрыш (точность при гибели процесса за 5 с) не стоит этого. Отклонено; можно вернуться, если M3-тест «через 1 минуту» должен звонить настройками пользователя после гибели процесса.
* **Отдельная таблица `test_alarm`** — точность БД без загрязнения `alarm`, но миграция v2 ради теста. Отклонено.
* **Старт `RingingService` из UI через `delay(5 s)`** — не через `setAlarmClock` (нарушение FR-REL-1), не переживает сон CPU/уход в фон, не проверяет ресивер и FSI. Отклонено.
* **`FireKind.TEST` вместо зарезервированного id** — id `UNSAVED` нельзя планировать и адресовать командами (`AlarmIntents.alarmId` отвергает 0), правок в сервисе/интентах больше. Отклонено.

## Consequences
* (+) Тест идёт настоящей цепочкой `setAlarmClock` → ресивер → FGS → FSI; в M4/M5 автоматически проверяет мелодию и миссию черновика.
* (+) Ни строки в БД, ни изменения `rescheduleAll`; расписание пользователя изолировано по URI.
* (−) Гибель процесса между «Тест» и звонком → звонок с настройками по умолчанию (не черновика).
* (−) `Long.MAX_VALUE` как зарезервированный id — соглашение; закреплено тестом «save/setEnabled/delete отвергают `TEST`» и тестом репозитория, что вставка не выдаёт его.
* Проверка: unit — `TestAlarmRunner` (снимок/без снимка, finish), движок не трогает репозиторий для `TEST`, `rescheduleAll` не отменяет тест; Robolectric — `ShadowAlarmManager` видит отдельный PendingIntent; эмулятор — «Тест» → звонок ≤ 7 с, затем экран выключен → «Тест» → экран включается (R1), тест + будильник пользователя в ту же минуту → очередь (R13).

## Related
ADR-006 §1–§2, §7; ADR-007 §1–§7; ADR-009; FR-EDIT-10, FR-REL-1, FR-REL-7 (M3), FR-RING-7, FR-FLAG-4; R1, R13.
