# ADR-013: Онбординг: когда показывается, состояние в DE DataStore, шаги и возврат из системных настроек

## Status
Proposed (2026-10-07, план M3). Реализует PRD §3.7; заменяет временный запрос `POST_NOTIFICATIONS` в `MainActivity` (ADR-007 §8). Первое использование DataStore (ADR-004 §6).

## Context
* PRD §3.7: первый запуск, по одному разрешению на экран, «Разрешить» и «Позже» (кроме критичных); возврат из системных настроек автоматически отмечает шаг. Шаг 6 (OEM) — пояснение + dontkillmyapp.com + `ACTION_APPLICATION_DETAILS_SETTINGS` + чек-бокс «Я сделал». Шаг 7 — опциональная подсказка про DND.
* **Противоречие в PRD:** шаги 1–5 помечены «критично», а «Позже» — «кроме критичных». Буквально — пользователь, отказавший в overlay или батарее, не может завершить онбординг. Ловушка недопустима (UX, политика Play).
* Шаг 2 (`USE_EXACT_ALARM`) на minSdk 34 всегда выполнен — экран без действия.
* Отзыв runtime-разрешения в системных настройках убивает процесс; выдача overlay/FSI — нет. Стек Nav3 и состояние экрана должны пережить смерть процесса.
* ADR-001: единое хранилище — DE; делегат `preferencesDataStore` запрещён. Каталог уже содержит `androidx-datastore-preferences` 1.2.0.
* NFR-2: холодный старт ≤ 800 мс (на эмуляторе уже на границе, перенос M2).

## Decision
1. **Состояние:**
   ```kotlin
   // :core:domain
   data class SetupState(val onboardingCompleted: Boolean = false, val oemBackgroundConfirmed: Boolean = false)
   interface SetupStateRepository {
       val state: Flow<SetupState>
       suspend fun completeOnboarding()
       suspend fun setOemBackgroundConfirmed(confirmed: Boolean)
   }
   ```
   Реализация `DataStoreSetupStateRepository` в `:core:data`: один `DataStore<Preferences>` (`@Singleton`, `PreferenceDataStoreFactory.create { deContext.preferencesDataStoreFile("app_prefs") }`, `@DeviceProtected` контекст). Тот же файл позже примет глобальные настройки PRD §3.9. Ошибка чтения (`IOException`) → `SetupState()` по умолчанию (онбординг покажется ещё раз — безопасно).
2. **Когда показывается:** при запуске `MainActivity`, если `onboardingCompleted == false` — стек `[OnboardingKey]` без нижней панели (ADR-014); иначе — вкладки. Обновление с M2 (флага нет) показывает онбординг один раз. Повторно из настроек онбординг не открывается — его роль выполняет экран здоровья. Решение о стартовом экране:
   * `MainActivity` держит системный splash, пока `SetupState` не прочитан — платформенный приём `content.viewTreeObserver.addOnPreDrawListener { ready }` (без `core-splashscreen`, < 20 строк); `BalarmApp` не компонует `NavDisplay`, пока состояние неизвестно;
   * сохранённый стек (поворот, смерть процесса) имеет приоритет над флагом;
   * завершение: `completeOnboarding()` → стек заменяется на корень вкладок (Back из списка не возвращает в онбординг).
3. **Шаги** — `enum class OnboardingStep(val item: HealthItem)` в `:feature:onboarding`: `NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN_INTENT, OVERLAY, BATTERY (BATTERY_OPTIMIZATION; при BACKGROUND_RESTRICTION — вариант экрана «снимите ограничение»), OEM_BACKGROUND, DO_NOT_DISTURB`. Правила (чистый reducer, unit-тесты):
   * **Пропуск выполненных:** показываются только шаги, чей статус ≠ OK по `healthReport` (ADR-012). Шаг 2 на практике пропускается всегда, DND — если будильники проходят фильтр. OEM показывается всегда, пока не подтверждён.
   * **Текущий шаг вычисляется**, а не хранится: первый шаг в порядке enum, который не OK и не в `skipped`. В `SavedStateHandle` хранятся только `skipped: Set<OnboardingStep>` и `attempted: Set<OnboardingStep>` — после смерти процесса шаг восстанавливается по фактическим статусам.
   * **«Позже»:** у RECOMMENDED/INFO — сразу; у CRITICAL — только после попытки (`attempted`), подписью «Продолжить без этого» и предупреждением «будильник может не сработать». Отказаться можно, застрять нельзя; последствия видны в баннере/здоровье.
   * **Возврат из настроек:** на `ON_RESUME` — новый `snapshot()`; если текущий шаг стал OK — переход к следующему автоматически (FR §3.7 «автоматически отмечает шаг»); если нет и шаг в `attempted` — показывается «Продолжить без этого». Результат runtime-запроса уведомлений — то же событие.
   * OEM: «Открыть настройки», ссылка dontkillmyapp.com (`ACTION_VIEW`, браузер; приложению `INTERNET` не нужен), чек-бокс «Я сделал» → `setOemBackgroundConfirmed(true)`.
   * После последнего шага — `completeOnboarding()` и колбэк `onFinished`. Отдельного экрана «Готово» нет.
4. **Контракт модуля** `:feature:onboarding` (`balarm.android.feature`): `@Composable fun OnboardingRoute(onFinished: () -> Unit, modifier: Modifier = Modifier)`; `OnboardingViewModel(checker: PermissionHealthChecker, setup: SetupStateRepository, savedState: SavedStateHandle)`; запуск исправлений — `rememberHealthFixLauncher` из `:core:permissions` (ADR-012 §4). Иллюстрации — авторские векторные CC0 (`docs/LICENSES.md`), без копирования Alarmy.
5. **`MainActivity`:** удаляется `requestNotificationsOnce()` (ADR-007 §8). `rescheduleAll(APP_LAUNCH)` остаётся как есть.

## Alternatives considered
* **SharedPreferences в DE (синхронно, как debug-флаги).** Нет асинхронности на старте, но второй механизм хранения настроек рядом с запланированным DataStore. Отклонено; вернуться, если замер NFR-2 покажет заметную задержку чтения.
* **`androidx.core:core-splashscreen`** — то же за счёт библиотеки; платформенный `OnPreDrawListener` укладывается в 20 строк. Отклонено.
* **Онбординг как отдельная Activity** — проще стартовое решение, но второй Compose-хост и дублирование темы/edge-to-edge (те же доводы, что ADR-009). Отклонено.
* **Показывать все 7 шагов всегда** — проще тестировать, но экран «точные будильники» без действия и шаг DND без проблемы — шум. Отклонено.
* **Хранить номер шага** — после возврата из настроек/смерти процесса номер расходится со статусами. Отклонено.

## Consequences
* (+) Пользователь не застревает; ни одного экрана без действия; восстановление после смерти процесса по построению.
* (+) Временный запрос разрешения из M1 убран.
* (−) Правка PRD §3.7 («Позже» у критичных — после попытки; пропуск выполненных шагов) — в T-docs.
* (−) `SavedStateHandle` внутри записи Nav3 — проверить в задаче (декоратор ViewModelStore); запасной путь — `rememberSaveable` в Route и передача в ViewModel событием.
* Проверка: unit — reducer шагов (пропуск OK, «Позже» после попытки, авто-переход, восстановление по статусам); Robolectric — репозиторий пишет в DE (`isDeviceProtectedStorage`), ошибка чтения → дефолт; Compose — шаги, кнопки RU/EN, fontScale 2f; `:app` — стартовый экран по флагу, замена стека после завершения, пересоздание Activity; эмулятор API 34/37 — чистая установка → онбординг → выдача каждого разрешения через системный экран → авто-переход; отзыв уведомлений в настройках во время онбординга (смерть процесса) → возврат на нужный шаг; холодный старт `am start -W` до/после (NFR-2).

## Related
ADR-001, ADR-004 §6, ADR-007 §8, ADR-009, ADR-012, ADR-014; PRD §3.7, §3.9, FR-REL-7, NFR-2, NFR-7; R12.
