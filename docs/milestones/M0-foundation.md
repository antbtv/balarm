# M0: Фундамент

| | |
|---|---|
| Статус | Done |
| Утверждён | 2026-09-28 |
| Завершён | 2026-09-29 |
| Требования | NFR-1, NFR-4, NFR-7, F-UI-01 (тема), FR-FLAG-1…6, PRD §6.1–6.2 |
| Definition of Done | `./gradlew build` зелёный локально и в CI; пустое приложение с тёмной темой `BalarmTheme` запускается на эмуляторе API 37; `.mcp.json` проверен (mobile-mcp видит эмулятор) |

## Цель
Рабочий многомодульный Android-проект с единой конфигурацией сборки, проверками качества и CI, на который дальше «навешиваются» этапы M1–M8. Пользовательского функционала нет — только тёмный экран-заглушка в стиле Balarm.

## Архитектура этапа
Резюме от architect (подробно — ADR-003):
* **Версии:** Gradle 9.7.1 · AGP 9.4.1 · Kotlin 2.4.10 (пин из-за detekt) · KSP 2.3.12 · Compose BOM 2026.09.00 · Hilt 2.60.1 · detekt 2.0.0-alpha.6 · ktlint-gradle 14.2.0 · JUnit4/Truth/Turbine/MockK/Robolectric 4.17. Сборка на **JDK 21**, `jvmTarget 17`.
* **SDK:** compileSdk = targetSdk = **37**, minSdk 26.
* **Модули M0:** `build-logic`, `:app`, `:core:model` (JVM), `:core:designsystem` (Android + Compose). Остальные — в этапе, где появляется их первый код.
* **Convention plugins:** `balarm.android.application`, `balarm.android.library`, `balarm.android.compose`, `balarm.jvm.library`, `balarm.hilt`, `balarm.quality` (detekt + ktlint, подключается остальными).
* **Идентификаторы:** `applicationId = com.antbtv.balarm` (без debug-суффикса); namespace = `com.antbtv.balarm.<путь.модуля>`.
* **Манифест:** ни одного разрешения (NFR-4); список допустимых разрешений ведётся в `config/permissions-allowlist.txt` и проверяется в CI.

## Решения на утверждение
- [ ] **ADR-001** — всё хранение только в device-protected storage; `directBootAware` на компонентах цепочки звонка (реализация в M1).
- [ ] **ADR-002** — foreground service звонка типа `systemExempted` (не `mediaPlayback`); звук строго `USAGE_ALARM` (исключение из background audio hardening Android 17).
- [ ] **ADR-003** — стек, версии, SDK 37, модули, convention plugins, CI.
- [ ] **JDK 21** ставится через sdkman (текущий 17 остаётся), нужен Robolectric с M1.
- [ ] **Палитра:** светлый `primary` `#F0383B` → `#D32F2F` (контраст 4.98:1); в тёмной `#FF4D4F` остаётся, текст на красных кнопках ≥ 19sp Bold (норма 3:1 для крупного текста). Правка PRD §4.2 и скилла `alarmy-ui`.
- [ ] **Эмулятор:** образы API 37 (+ 36) ≈ 2×2 ГБ на диске (свободно 24 ГБ). Остальные API (26–34) — в M1 для тестов надёжности.

## Задачи
| ✓ | ID | Задача | FR/NFR | Оценка | Зависит от | Commit |
|---|---|---|---|---|---|---|
| [x] | M0-T01 | Гигиена репозитория и окружение (JDK 21) | — | S | — | |
| [x] | M0-T02 | Gradle wrapper, settings, version catalog | NFR-1 | S | T01 | |
| [x] | M0-T03 | build-logic: convention plugins | NFR-1 | M | T02 | |
| [x] | M0-T04 | `:core:model` — JVM-модуль | — | S | T03 | |
| [x] | M0-T05 | 🎨 `:core:designsystem` — токены, тема, типографика | F-UI-01, NFR-7 | M | T03 | |
| [x] | M0-T06 | `:app` — каркас: Application + Hilt, манифест, MainActivity | NFR-4 | S | T03, T05 | |
| [x] | M0-T07 | 🎨 Экран-заглушка, XML-тема, строки RU/EN | F-UI-01, NFR-7 | S | T06 | |
| [x] | M0-T08 | Качество: detekt + ktlint, зелёный `./gradlew build` | — | S | T04–T07 | |
| [x] | M0-T09 | CI: GitHub Actions | — | S | T08 | |
| [x] | M0-T10 | Эмулятор, AVD, установка, проверка mobile-mcp | — | M | T07 | |
| [x] | M0-T11 | Feature flags: конфиг, генерация `FeatureFlags`, debug-экран | FR-FLAG-1…6 | S | T04, T06 | |
| [x] | M0-T-test | Тестирование этапа (tester) | — | S | T01–T10 | |
| [x] | M0-T-review | Ревью этапа (reviewer) | — | S | T-test | |
| [x] | M0-T-docs | Правки PRD / MCP.md / alarmy-ui | — | S | T-review | |

### M0-T01 — Гигиена репозитория и окружение
**Описание:** удалить IntelliJ-шаблон (`src/Main.kt`, `Balarm.iml`, модульные файлы `.idea`); Android-`.gitignore` (`.gradle/`, `build/`, `local.properties`, `.kotlin/`, `*.iml`, `.idea/*` кроме codeStyles); `.editorconfig` (ktlint `android_studio`); установить JDK 21 через sdkman; `local.properties` с `sdk.dir`.
**Модули:** корень
**Критерии приёмки:**
- [ ] `git status` не показывает IDE-мусор и build-артефакты
- [ ] `java -version` в проекте = 21 (sdkman `.sdkmanrc`)
**Тесты:** —

### M0-T02 — Gradle wrapper, settings, version catalog
**Описание:** wrapper 9.7.1; `settings.gradle.kts` (`pluginManagement { includeBuild("build-logic") }`, `FAIL_ON_PROJECT_REPOS`, репозитории google/mavenCentral, `rootProject.name = "Balarm"`); `gradle/libs.versions.toml` со всеми версиями из ADR-003 (включая будущие Room/DataStore/Media3 — только в каталоге); `gradle.properties` (configuration cache, build cache, parallel, `android.useAndroidX`, JVM args).
**Модули:** корень
**Критерии приёмки:**
- [ ] `./gradlew help` проходит, configuration cache сохраняется
**Тесты:** —

### M0-T03 — build-logic: convention plugins
**Описание:** included build `build-logic/convention` с плагинами `balarm.android.application`, `balarm.android.library` (namespace из пути), `balarm.android.compose` (через `CommonExtension`), `balarm.jvm.library`, `balarm.hilt` (KSP), `balarm.quality` (detekt + ktlint, привязка к `check`). Общие константы SDK/JVM в одном месте. Без `subprojects {}` в корне.
**Модули:** `build-logic`
**Критерии приёмки:**
- [ ] Плагины резолвятся по id из `libs.plugins`
- [ ] Изменение minSdk/compileSdk — в одном файле
**Тесты:** проверяются сборкой T04–T06

### M0-T04 — `:core:model`
**Описание:** JVM-модуль (`balarm.jvm.library`), пакет `com.antbtv.balarm.core.model`, один placeholder-тип + тест, чтобы модуль и тестовая конфигурация были «живыми».
**Критерии приёмки:**
- [ ] Модуль не имеет Android-зависимостей (`./gradlew :core:model:dependencies` без androidx)
- [ ] `./gradlew :core:model:test` зелёный

### M0-T05 — 🎨 `:core:designsystem` (agent: ui-developer)
**Описание:** по скиллу `alarmy-ui` и PRD §4.2: `BalarmColors` (тёмная/светлая, с утверждённой правкой primary), `ColorScheme` M3, `BalarmTypography` (timeHuge 96sp, timeLarge 44sp, `tnum`), `BalarmShapes`, `BalarmDimens`, `BalarmTheme(darkTheme = true по умолчанию)`, `CompositionLocal` для кастомных токенов (success/warning/textSecondary). Компонентов пока не делаем — только тема. Превью палитры.
**Модули:** `:core:designsystem`
**Критерии приёмки:**
- [ ] Тёмная тема по умолчанию, светлая доступна параметром
- [ ] Все значения — из PRD §4.2 (с правкой), нигде не продублированы
**Тесты:** unit-тест контраста WCAG для пар text/background, textSecondary/surface(-Variant), onPrimary/primary (≥ 4.5:1, для onPrimary/primary в тёмной — ≥ 3:1 как крупный текст)

### M0-T06 — `:app` каркас
**Описание:** `balarm.android.application` + compose + hilt; `BalarmApplication` (`@HiltAndroidApp`, пустой `onCreate`, безопасный для Direct Boot по ADR-001); `MainActivity` (`@AndroidEntryPoint`, `enableEdgeToEdge`, `setContent { BalarmTheme { … } }`); манифест без разрешений; `generateLocaleConfig`.
**Критерии приёмки:**
- [ ] `./gradlew :app:assembleDebug` собирает APK
- [ ] В merged manifest нет `uses-permission` (проверка `aapt2 dump permissions` / `processDebugManifest` output)

### M0-T07 — 🎨 Экран-заглушка (agent: ui-developer)
**Описание:** XML-тема `Theme.Balarm` с `windowBackground #0E0F14` и прозрачными системными барами (нет белой вспышки при старте); `SplashScreen` API по вкусу — не нужно; экран-заглушка: название «Balarm» + подпись `timeLarge`-шрифтом текущего времени (статично) — проверка типографики; `strings.xml` EN + `values-ru`; временная adaptive-иконка (буква/будильник из Material Symbols, не Alarmy).
**Критерии приёмки:**
- [ ] При запуске нет белого кадра
- [ ] Строки переключаются RU/EN
- [ ] Превью: тёмная/светлая, fontScale 2f
**Тесты:** Compose UI-тест (Robolectric): заголовок отображается

### M0-T08 — Качество
**Описание:** `config/detekt/detekt.yml` (правила Compose — naming функций с заглавной), ktlint через `.editorconfig`; baseline пустые; устранить все замечания; android lint `warningsAsErrors` для ключевых проверок, `abortOnError`.
**Критерии приёмки:**
- [ ] `./gradlew build` зелёный (включает test, lint, detekt, ktlintCheck)
- [ ] Намеренная ошибка стиля валит `check` (проверить и откатить)

### M0-T09 — CI
**Описание:** `.github/workflows/ci.yml`: push/PR в `main`, ubuntu-latest, Temurin 21, `gradle/actions/setup-gradle`, `./gradlew build --continue`, выгрузка отчётов при падении, concurrency cancel-in-progress, timeout 30 мин; шаг-проверка разрешений: merged manifest содержит только разрешения из allowlist-файла `config/permissions-allowlist.txt` (в M0 — пустой; `INTERNET` добавится в M6 вместе с FR-TTS, остальные — в M1/M3).
**Критерии приёмки:**
- [ ] YAML валиден (`actionlint`, если доступен)
- [ ] Прогон — после появления GitHub-репозитория (remote пока нет → ⏭ с пометкой)

### M0-T10 — Эмулятор и MCP
**Описание:** `sdkmanager "emulator" "system-images;android-37.0;google_apis;x86_64"` (+ 36), AVD `balarm_api37`, проверка KVM; запуск эмулятора, `./gradlew :app:installDebug`, запуск приложения, скриншот через mobile-mcp; зафиксировать версию `@mobilenext/mobile-mcp` в `.mcp.json` вместо `@latest`.
**Критерии приёмки:**
- [ ] `adb devices` видит эмулятор, mobile-mcp — тоже
- [ ] Скриншот: тёмный фон `#0E0F14`, заголовок, без белой вспышки
**Тесты:** визуальная проверка

### M0-T11 — Feature flags
**Описание:** `config/features.properties` со стартовым реестром из PRD §3.12; Gradle-задача в convention plugin (`balarm.featureflags`) генерирует `FeatureFlags.kt` в `:core:model` (подключена к компиляции, совместима с configuration cache); интерфейс `FeatureFlagProvider` (`:core:model`) + реализация в `:app`: release — значения из `FeatureFlags`, debug — `FeatureFlags` + переопределения из DataStore; debug-only `FeatureFlagsActivity` (список переключателей + «Сбросить») в `src/debug` `:app`.
**Модули:** `build-logic`, `:core:model`, `:app`
**Критерии приёмки:**
- [ ] Изменение значения в `features.properties` меняет `FeatureFlags` после сборки
- [ ] Лишний/отсутствующий ключ → понятная ошибка сборки
- [ ] В release APK нет `FeatureFlagsActivity` (проверка `apkanalyzer manifest print`)
- [ ] В debug переключение флага применяется без пересборки
**Тесты:** unit — генератор (парсинг, валидация, ошибки); unit — `FeatureFlagProvider` debug-переопределение

### M0-T-test — Тестирование этапа (agent: tester)
Чистый клон → `./gradlew build`; unit-тесты; переключение флага в debug-экране; установка на эмулятор; проверка NFR-4 (нет разрешений). Сценарии R1–R18 — не применимы (нет движка будильника).

### M0-T-review — Ревью этапа (agent: reviewer)
Весь диф M0: структура build-logic, каталог версий, манифест, тема.

### M0-T-docs — Документация
Правки из отчёта architect: NFR-1 → 37; §7/FR-REL-5 → `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`; §9.3 + MCP.md → список эмуляторов с API 37; §4.2 → палитра; §6.3 → `directBootAware` на компонентах, не на `<application>`; §6.1 → Navigation Compose vs Navigation3 выбрать ADR в M2; новый сценарий **R19** (Android 17 background audio hardening; R20–R21 уже заняты озвучкой); обновить `alarmy-ui`; статусы ADR → Accepted.

## Риски
| Риск | Митигация |
|---|---|
| IntelliJ IDEA не синхронизирует AGP 9.4 | откат на AGP 9.3.3 или Android Studio Quail 4+ |
| detekt 2.0 alpha + пин Kotlin 2.4.10 | при блокере временно снять detekt с `check`, ktlint остаётся |
| Hilt 2.60.1 × Kotlin 2.4.10 × KSP 2.3.12 | проверяется первым в T06; фолбэк — соседние патч-версии |
| Нет KVM / аппаратного ускорения для эмулятора | проверить `/dev/kvm` в T10; иначе — реальное устройство по USB |
| На CI-раннере нет platform 37 | `android-actions/setup-android` |

## Добавлено по ходу
- **Память (2026-09-29):** ноутбук завис от трёх параллельных Gradle-демонов (по 4 ГБ). Введены лимиты: `gradle.properties` (Xmx2560m, workers.max=2, Kotlin in-process, idle timeout 10 мин), тестовые JVM (1 ГБ, 1 fork), правило «одна сборка за раз», эмулятор headless с 2 ГБ — раздел «Ресурсы машины» в CLAUDE.md.
- Robolectric на JDK 21 + SDK 37: нужны `--add-opens java.base/java.io` и `--add-exports java.base/jdk.internal.access`; Espresso поднят до 3.7.0 (3.5.0 из compose ui-test падает на API 37).
- Статус-бар: `enableEdgeToEdge()` по умолчанию брал светлую системную тему → тёмные иконки на тёмном фоне. Исправлено `SystemBarStyle.dark` (найдено на эмуляторе).
- `scripts/check-permissions.sh` сверяет release-манифест с `config/permissions-allowlist.txt` и проверяет отсутствие debug-компонентов (FR-FLAG-5); в CI.
- Ревью (⚠️ Approve with comments), исправлено: `-Xjdk-release=17` для JVM-модулей; убран toolchain 17 из build-logic; CI `permissions: contents: read`; разбор манифеста через XML; `allowBackup=false` до решения о бэкапе; debug-переопределение, равное конфигу, не хранится; мелочи в генераторе.
- Реализация FR-FLAG отличается от первой редакции PRD (`enum Feature` + `FeatureFlagProvider`, SharedPreferences в DE-storage) — PRD §3.12 обновлён.

## Результаты проверок
- `./gradlew build --continue` — зелёный (25 unit-тестов: генератор флагов 7, Feature 3, AlarmId 2, контраст 6, заглушка 3, debug-флаги 4 + lint/detekt/ktlint). Сборка из «чистой копии» (только файлы для git) — зелёная.
- Эмулятор API 37: приложение запускается, тёмная тема без белой вспышки, статус-бар читаемый; debug-экран флагов переключает флаг, переопределение сохраняется в `/data/user_de/…` и переживает перезапуск; release APK без `FeatureFlagsActivity` (apkanalyzer).
- mobile-mcp: видит `emulator-5554` (balarm api37, Android 17), запускает приложение, читает элементы экрана (включая contentDescription времени). Понадобилось: `ANDROID_HOME`/`PATH` в `.mcp.json` и `MOBILEMCP_LEGACY_ROBOT=1` (встроенный mobilecli 1.0.13 не видит локальный эмулятор).
- ⏳ CI — прогнать после первого push в GitHub.

## Уроки
- Не запускать сборки параллельно из основной сессии и субагентов; проверять `free -m` перед тяжёлыми шагами.
- Эмулятор находит визуальные баги, которые не ловят unit-тесты (статус-бар) — короткая проверка на эмуляторе в каждой UI-задаче окупается.

## Перенесено в следующий этап
- **M1:** решение о бэкапе (сейчас `allowBackup=false`; данные в DE-storage авто-бэкап не захватывает) — ADR вместе с `:core:data`.
- **M1:** `AlarmId` не допускает 0 (Room autoGenerate) — продумать тип для несохранённого будильника; `requestCode` PendingIntent без коллизий Long→Int.
- **M1:** эмуляторы API 26/29/31/33/34/36 для сценариев надёжности (ставить по одному, диск ≈ 17 ГБ свободно).
- **M2:** `success`/`warning` в светлой теме < 3:1 — не использовать как текст, пересмотреть при первых компонентах; `labelLarge = buttonLarge` раздувает `TextButton` — проверить визуально; lint/ktlint для `build-logic`.
- **M7:** `SystemBarStyle` по теме приложения при включении `feature.lightTheme`.
