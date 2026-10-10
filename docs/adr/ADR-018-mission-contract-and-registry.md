# ADR-018: Контракт миссий: непрозрачный `MissionSpec` в модели, плагины через реестр, редактор и превью

## Status
Proposed (2026-10-10, план M5). Уточняет PRD §6.4 (`MissionConfig = sealed`) и скилл `add-mission` (enum `MissionType`, `sealed interface MissionConfig` в api) — см. «Правки» в конце.

## Context
* FR-MIS-4: каждая миссия реализует общий контракт (`MissionType`, `MissionConfig`, `MissionState`, `MissionScreen`); **добавление миссии не требует правок ядра** (`:core:*`, `:feature:ringing`). FR-MIS-1/5, FR-EDIT-6, FR-FLAG-3 (выключенная миссия → будильник звонит без миссии, данные не теряются).
* `Alarm` живёт в `:core:model` (чистый Kotlin), хранится в Room (`:core:data`), читается в Direct Boot и уходит в `RingingState` (ADR-007). Если конфиг миссии — `sealed`-иерархия в `:core:model` (PRD §6.4), каждая новая миссия правит модель, маппер и миграцию — нарушение FR-MIS-4. `sealed interface` в `:feature:missions:api` (скилл) вообще нельзя реализовать в другом модуле (Kotlin: наследники sealed — в том же модуле и пакете), а enum `MissionType` в api — тоже правка ядра на каждую миссию.
* `:core:alarm` (сервис, уведомление) должен знать, **требует ли** звонок миссию (кнопка «Отключить» в уведомлении, ADR-007 §2.1), но не может зависеть от `:feature:*`.
* В v0.1 одна миссия на будильник; мульти-миссии — бэклог.

## Decision
1. **Модель (`:core:model`) — непрозрачная ссылка, без знания о конкретных миссиях.**
   ```kotlin
   @JvmInline value class MissionTypeId(val value: String) {      // "math"; стабильный ключ, хранится в БД
       init { require(value.matches(Regex("[a-z][a-z0-9_]{0,31}"))) }
   }
   /** [config] — JSON, который пишет и читает только плагин миссии; ядро его не разбирает. */
   data class MissionSpec(val type: MissionTypeId, val config: String)
   // Alarm: + val mission: MissionSpec? = null
   ```
2. **Домен (`:core:domain/mission`) — один вопрос к миссиям, нужный ядру.**
   ```kotlin
   interface MissionCatalog {
       /** Миссия этого типа есть в сборке и её флаг включён (FR-FLAG-3). */
       fun isAvailable(type: MissionTypeId): Boolean
   }
   fun Alarm.effectiveMission(catalog: MissionCatalog): MissionSpec? = mission?.takeIf { catalog.isAvailable(it.type) }
   ```
   Реализация — `MissionRegistry` в `:feature:missions:api` (Hilt-биндинг там же). `FeatureFlagProvider` читает DE `SharedPreferences` синхронно (FR-FLAG-5) — работает в Direct Boot.
3. **Контракт плагина (`:feature:missions:api`, Android + Compose; зависит от `:core:model`, `:core:domain`, `:core:designsystem`).**
   ```kotlin
   interface MissionDefinition<C : Any> {
       val type: MissionTypeId
       val feature: Feature                         // флаг FR-FLAG, напр. Feature.MISSION_MATH
       @get:StringRes val titleRes: Int
       val icon: ImageVector
       val defaultConfig: C
       val configSerializer: KSerializer<C>         // формат JSON = контракт хранения; несовместимое изменение → новый тип или миграция
       fun normalize(config: C): C                  // зажим диапазонов (битые/старые значения не роняют звонок)
       @Composable fun summary(config: C): String   // «Math · уровень 2 · ×3» для списка/редактора
       @Composable fun ConfigEditor(config: C, onChange: (C) -> Unit)
       @Composable fun Content(config: C, session: MissionSession)   // MissionScreen из FR-MIS-4
   }
   enum class MissionMode { RINGING, PREVIEW }
   /** Хост ↔ миссия. Миссия не трогает звук, сервис и навигацию. */
   interface MissionSession {
       val mode: MissionMode
       val seed: Long                               // генерация задач детерминирована по seed (тесты)
       val resetSignal: Flow<Unit>                  // бездействие: сбросить прогресс ТЕКУЩЕЙ задачи (FR-MIS-2)
       fun onInteraction()                          // любое касание/ввод — продлевает таймер бездействия
       fun onProgress(done: Int, total: Int)        // «2/5» в MissionProgressBar хоста
       fun onCompleted()                            // идемпотентно на стороне хоста
   }
   ```
   `MissionState` из FR-MIS-4 — внутренний immutable `UiState` плагина (ViewModel плагина, ключ — `seed`), не часть общего контракта.
4. **Реестр.** Каждый модуль миссии регистрирует себя `@Binds @IntoMap @StringKey("math")` → `Map<String, MissionDefinition<*>>`. `MissionRegistry` (`@Singleton`, реализует `MissionCatalog`):
   * `available(): List<MissionDefinition<*>>` — только с включённым флагом (порядок — по `titleRes`/регистрации);
   * `resolve(spec: MissionSpec?): ResolvedMission?` — `null`: миссии нет, тип неизвестен или флаг выключен; битый JSON → `defaultConfig` + событие лога (миссия остаётся — безопаснее для G2); `ResolvedMission` захватывает `C` и отдаёт `@Composable Content(session)`/`summary()`, чтобы вызывающие не работали со star-projection;
   * `encode(definition, config): MissionSpec`.
   JSON: `Json { ignoreUnknownKeys = true; encodeDefaults = true }`.
5. **Где реестр используется.** `:core:alarm` — только `MissionCatalog` (кнопка «Отключить» в уведомлении, `RingingState`, ADR-019). `:feature:ringing` и `:feature:alarmedit` зависят от `:feature:missions:api` (api-модуль — не «другая фича»). Модули миссий подключает только `:app`; `:feature:ringing` не зависит ни от одной конкретной миссии.
6. **Редактор (FR-EDIT-6, флаг на каждую миссию).** В `:feature:alarmedit` — строка «Миссия» (`SettingRow`, summary или «Без миссии»), видна, если `registry.available()` не пуст. Тап → `onOpenMission(MissionSpec?)` → `:app` открывает `MissionSettingsKey` → `MissionSettingsRoute(selected: MissionSpec?, onPicked: (MissionSpec?) -> Unit, onPreview: (MissionSpec) -> Unit, onClose)` из `:feature:missions:api`: «Без миссии» + список доступных, `ConfigEditor` выбранной, кнопка «Попробовать». Результат — как у пикера мелодий (`BalarmNavState`, `rememberSaveable`, ADR-009 §3 уточнения M4). Будильник с миссией, флаг которой выключен, в редакторе показывает «Без миссии», но **сохраняет `mission` как есть**, пока пользователь не выберет другое (FR-FLAG-3: данные не ломаются).
7. **Превью (FR-MIS-5).** `MissionPreviewKey(spec)` → `MissionPreviewRoute(spec, onClose)` в `:feature:missions:api`: тот же `MissionFrame` (прогресс-бар, полоска бездействия, слот `Content`) с `MissionMode.PREVIEW` — без звука, без `RingingController`, с кнопкой «Закрыть» и разрешённым Back; бездействие сбрасывает задачу, как в звонке. `MissionFrame` (stateless UI-оболочка) общий для звонка и превью.
8. **Math (`:feature:missions:math`).** `MathConfig(level: Int = 2, count: Int = 3)` (`@Serializable`, `normalize` зажимает 1..5 / 1..10); `MathGenerator(random: Random)` — чистый Kotlin, уровни по PRD §3.5 (L1 `a+b` 1–2 знака; L2 `a+b-c`; L3 `a×b+c`; L4 `a×b+c×d` 2 знака; L5 `(a×b)-c+d` 3 знака), ответы в `Int` (max 999·999+999 < 2³¹), отрицательные — с L3; ввод — своя клавиатура (цифры, «−» с L3, стереть, «OK»), до 7 символов; неверно → `ShakeOnError` + haptic, ввод очищается, пример тот же; бездействие → очищается ввод текущего примера, решённые сохраняются. Флаг `feature.mission.math` (есть в реестре, `false` до T-docs M5).
9. **Хранение** — колонки `alarm.mission_type`, `alarm.mission_config` (ADR-020).

## Alternatives considered
* **`sealed MissionConfig` в `:core:model` (PRD §6.4)** — типобезопасно, но каждая миссия правит модель, маппер и, возможно, схему: нарушает FR-MIS-4. Отклонено.
* **`sealed interface MissionConfig` в api + enum `MissionType` (скилл)** — sealed не реализуется в другом модуле; enum — правка ядра на каждую миссию. Отклонено.
* **Открытый полиморфизм kotlinx.serialization (`SerializersModule` с подклассами из плагинов)** — тип конфига жил бы в модели, но `:core:data` пришлось бы собирать модуль сериализации из DI плагинов, а неизвестный дискриминатор (миссия удалена из сборки) ломал бы чтение всего `Alarm`. Отклонено: JSON непрозрачен для ядра, разбирает только плагин.
* **Отдельная таблица `alarm_mission`** — готова к мульти-миссиям, но лишний JOIN на звонке и миграция ради бэклога. Отклонено до появления мульти-миссий (тогда — новая таблица и перенос двух колонок).
* **Хост и превью в отдельном модуле `:feature:missions:host`** — чище api, но ещё один модуль ради ~3 composable. Отклонено; пересмотреть, если api разрастётся.

## Consequences
* (+) Новая миссия = новый модуль + строка в `config/features.properties` + подключение в `:app`; ядро, схема и `:feature:ringing` не меняются.
* (+) Выключенная или удалённая из сборки миссия не ломает чтение и звонок: будильник звонит без миссии, данные целы.
* (−) Конфиг миссии в БД — строка без проверки схемой; корректность держит плагин (`normalize`, `ignoreUnknownKeys`, тесты round-trip).
* (−) Ключ `MissionTypeId` и формат JSON — вечный контракт хранения.
* Проверка: unit — `MathGenerator` (детерминизм по seed, все уровни, диапазоны, знак), `MathConfig` round-trip и мусорный JSON, `MissionRegistry.resolve` (нет/неизвестный/флаг выкл./битый JSON); Compose UI — Math (верно/неверно/сброс), `MissionSettingsRoute`, превью; R16 в превью без звука.

## Related
ADR-007, ADR-009, ADR-011, ADR-019, ADR-020; FR-MIS-1…5, FR-EDIT-6, FR-FLAG-2…4, FR-LIST-1, NFR-6; PRD §3.5, §6.2, §6.4.

**Правки (T-docs M5):** PRD §6.4 — `MissionConfig = sealed` → `MissionSpec(type, config JSON)`; скилл `add-mission` — контракт по п. 3–4 (без enum и sealed), регистрация `@StringKey`, флаг в `MissionDefinition.feature`.
