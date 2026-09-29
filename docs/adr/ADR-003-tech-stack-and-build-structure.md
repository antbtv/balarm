# ADR-003: Технологический стек, SDK-уровни и структура сборки

## Status
Accepted (2026-09-28, утверждено вместе с планом M0). Предложено 2026-09-28. Версии проверены по developer.android.com, Maven Central, Google Maven и Gradle Plugin Portal на 2026-09-28.

## Context
* Проект создаётся с нуля (M0). PRD §6.1–6.2 задаёт стек и целевой список модулей; NFR-1 — minSdk 26, target/compile = «последний стабильный».
* На дату решения: Android 17 (API 37) стабилен; AGP 9.x работает со встроенной поддержкой Kotlin (built-in Kotlin, плагин `org.jetbrains.kotlin.android` не применяется) и только с новым DSL (`CommonExtension` без type-параметров, без `BaseExtension`) — это влияет на convention plugins.
* detekt 1.23.8 (последний стабильный, собран под Kotlin 2.0/AGP 8) не поддерживает built-in Kotlin AGP 9; поддержка есть только в detekt 2.0.0-alpha.6, собранном под Kotlin 2.4.10.
* Robolectric для SDK 36+ требует JDK 21 на рантайме тестов (android-all jar'ы скомпилированы под Java 21).

## Decision

### 1. SDK
| Параметр | Значение | Обоснование |
|---|---|---|
| `minSdk` | 26 | NFR-1 |
| `compileSdk` | 37 | последний стабильный, поддерживается AGP 9.1.1+ |
| `targetSdk` | 37 | см. ниже |
| Build Tools | не задавать явно (AGP 9.4 берёт 36.0.0; установленный 36.1.0/37.0.0 не мешают) | меньше ручных пинов |

**targetSdk 37, а не 36:** (а) Google Play требует target не старше года — к релизу (M8, ~2027) 37 станет обязательным, миграция посреди проекта дороже; (б) главное изменение Android 17 для нас — background audio hardening — частично действует на **все** приложения вне зависимости от target, а для target 37 у будильника есть явное исключение (exact alarm + `USAGE_ALARM`), которое мы и так выполняем (ADR-002); (в) остальное (нельзя отказаться от ресайза на sw≥600dp, BAL-hardening для `PendingIntent`/`IntentSender`, reflection на `static final`) нас затрагивает минимально. Target 36-поведение (обязательный edge-to-edge, predictive back по умолчанию) учитываем в любом случае.

### 2. Версии (version catalog `gradle/libs.versions.toml`)
| Компонент | Версия | Примечание |
|---|---|---|
| Gradle wrapper | **9.7.1** | AGP 9.4 требует ≥ 9.6.0; 9.8.0 вышел 2026-09-24 — перейти после 9.8.1 |
| AGP | **9.4.1** | Gradle ≥ 9.6.0, JDK ≥ 17, max API 37. IDE: Android Studio Quail 4 (2026.1.4)+. Фолбэк: 9.3.3, если IDE не синхронизирует 9.4 |
| Kotlin (KGP, `plugin.compose`, `plugin.serialization`) | **2.4.10** | пин под detekt 2.0.0-alpha.6; 2.4.20 — после выхода совместимого detekt. AGP тянет KGP 2.2.10 → версию задаём явно в build-logic/root |
| KSP | **2.3.12** | KSP2, версия не привязана к Kotlin; kapt не используем |
| Compose BOM | **2026.09.00** | ui/foundation 1.12.1, material3 1.4.0 |
| Dagger/Hilt | **2.60.1** | требует AGP 9+, Gradle 9.1+; процессинг через KSP |
| androidx.hilt | 1.4.0 | `hilt-lifecycle-viewmodel-compose` (с M2) |
| Room | 2.8.5 **или** room3 3.0.3 | выбор — ADR в M1 (в M0 не подключается) |
| Navigation | navigation-compose 2.10.2 **или** navigation3 1.2.0 | выбор — ADR в M2 (в M0 не подключается) |
| Lifecycle / Activity / Core-ktx | 2.11.0 / 1.13.0 / 1.19.1 | |
| DataStore | 1.2.0 | с M1 |
| Media3 | 1.11.1 | только если выберем в ADR по звуку (M1/M4) |
| kotlinx-coroutines / kotlinx-serialization | 1.11.0 / 1.11.0 | |
| detekt | **2.0.0-alpha.6** (plugin id `dev.detekt`) | единственная версия с AGP 9 built-in Kotlin; alpha допустима — не влияет на рантайм |
| compose-rules for detekt (`io.nlopez.compose.rules:detekt`) | 0.6.7 | опционально; подключаем, только если совместим с detekt 2.0 alpha |
| ktlint-gradle (`org.jlleitschuh.gradle.ktlint`) / ktlint | **14.2.0** / 1.8.0 | 14.1.0+ поддерживает built-in Kotlin |
| JUnit4 / Truth / Turbine / MockK / Robolectric | 4.13.2 / 1.4.5 / 1.2.1 / 1.14.11 / 4.17 | Robolectric 4.17 поддерживает SDK 37 |
| JDK | сборка на **JDK 21** (локально и в CI), `jvmTarget`/`compileOptions` = **17** | JDK 17 хватает для M0; с M1 Robolectric на SDK 36/37 требует 21 |

Правило: только стабильные версии, кроме явно помеченных в этой таблице (detekt alpha). Обновления — отдельными задачами, не попутно.

### 3. Модули в M0
`build-logic` (included build), `:app`, `:core:model` (чистый Kotlin/JVM), `:core:designsystem` (Android library + Compose). Остальные модули PRD §6.2 создаются в этапе, где у них появляется первый код (`:core:domain`, `:core:data`, `:core:alarm` — M1; `:feature:*` — M2+; `:core:permissions` — M3; `:feature:missions:*` — M5). Пустые модули «на вырост» не создаём: они удлиняют конфигурацию и ничего не проверяют. `:core:model` пустой, но нужен, чтобы в M0 проверить JVM-convention plugin и правило «без Android-зависимостей».

### 4. Convention plugins (`build-logic/convention`, префикс id `balarm.`)
| Plugin id | Применяет / настраивает | В M0 |
|---|---|---|
| `balarm.android.application` | `com.android.application`; compile/target/min SDK; Java/Kotlin target 17; buildTypes (release без minify до M8); `androidResources.generateLocaleConfig = true` (per-app language); lint (`abortOnError`, `checkDependencies`, baseline); подключает `balarm.quality` | да |
| `balarm.android.library` | `com.android.library`; SDK/target 17; `namespace` выводится из пути модуля (`:core:designsystem` → `com.antbtv.balarm.core.designsystem`); lint; `balarm.quality` | да |
| `balarm.android.compose` | `org.jetbrains.kotlin.plugin.compose`, `buildFeatures.compose`, Compose BOM, `ui-tooling-preview` + `debugImplementation(ui-tooling, ui-test-manifest)`; работает через `CommonExtension` для app и library | да |
| `balarm.jvm.library` | `org.jetbrains.kotlin.jvm`, target 17, JUnit4 + Truth; `balarm.quality` | да |
| `balarm.hilt` | `com.google.devtools.ksp` + `com.google.dagger.hilt.android`, `hilt-android` + `ksp(hilt-compiler)` | да (только `:app`) |
| `balarm.quality` | `dev.detekt` (общий `config/detekt/detekt.yml`, baseline, `buildUponDefaultConfig`) + `org.jlleitschuh.gradle.ktlint` (`.editorconfig`, `android_studio` style, исключение для `@Composable`-имён); оба подвешены к `check` | да (внутренний, применяется другими) |
| `balarm.android.room` | KSP Room, `room { schemaDirectory }` для тестов миграций | M1 |
| `balarm.kover` / покрытие | отчёт и порог для `:core:domain` (NFR-6) | M1 |
| `balarm.android.feature` | library + compose + hilt + lifecycle/ViewModel + `:core:designsystem` | M2 |

Качество не настраивается из корня через `subprojects {}` (совместимость с configuration cache / isolated projects).

### 5. Идентификаторы
* `applicationId = "com.antbtv.balarm"` (закрывает открытый вопрос PRD §11.1; после публикации неизменяем). Без `applicationIdSuffix` для debug — adb-скрипты и `dumpsys alarm | grep com.antbtv.balarm` работают для любого билда.
* `namespace` модулей: `com.antbtv.balarm.<путь модуля через точки>`; `:app` → `com.antbtv.balarm`; `:feature:missions:math` → `com.antbtv.balarm.feature.missions.math`. Kotlin-пакеты совпадают с namespace. AGP 9 по умолчанию требует уникальности (`android.uniquePackageNames`).

### 6. Прочее
* `settings.gradle.kts`: `pluginManagement { includeBuild("build-logic") }`, `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, `TYPESAFE_PROJECT_ACCESSORS`.
* `gradle.properties`: configuration cache, build cache, parallel включены.
* Ресурсы строк: `values/` = EN (фолбэк для прочих локалей), `values-ru/` = RU; `resources.properties` с `unqualifiedResLocale=en`.
* UI-хост: одна `ComponentActivity` + `enableEdgeToEdge()`; без AppCompat/Material Components (XML-тема — платформенная `NoActionBar` с `windowBackground` = `#0E0F14`, чтобы не было белой вспышки).
* CI: GitHub Actions, `ubuntu-latest`, Temurin 21, `gradle/actions/setup-gradle` (кэш + проверка wrapper), команда `./gradlew build --continue` (включает unit-тесты, lint, detekt, ktlintCheck через `check`), артефакты отчётов при падении. Эмулятор в CI — не раньше M1/M2.

## Alternatives considered
* **targetSdk 36** — формально соответствует NFR-1 в текущей редакции и требованию Play на 2026 год; отклонено: гарантированная миграция на 37 до релиза, а ключевое ограничение Android 17 (фоновое аудио) всё равно действует.
* **detekt 1.23.8** — стабильный, но не видит исходники при AGP 9 built-in Kotlin и собран под Kotlin 2.0. Вариант «AGP 9 + `android.builtInKotlin=false`» — временный opt-out, удаляемый в AGP 10. Отклонено.
* **Только ktlint через detekt-ktlint-wrapper** — меньше плагинов, но CLAUDE.md фиксирует задачу `ktlintCheck`, а ktlint-gradle даёт `ktlintFormat`. Отклонено.
* **kapt для Hilt** — медленнее, в режиме поддержки. Отклонено в пользу KSP.
* **Все модули PRD §6.2 скелетами сразу** — отклонено (см. п. 3, принцип «нет абстракции без потребителя»).
* **JDK 17 везде** — работает для M0, но в M1 ломает Robolectric на SDK 37; менять CI дважды нет смысла.

## Consequences
* (+) Один источник версий; модули — 3–10 строк `build.gradle.kts`.
* (+) Готовность к требованию Play 2027 без миграции target.
* (−) Kotlin пинится на 2.4.10 до выхода detekt 2.0 (alpha → stable); при несовместимостях detekt можно временно отключить в `check`, не трогая рантайм.
* (−) IntelliJ IDEA может отставать в поддержке AGP 9.4 → фолбэк AGP 9.3.3 (Gradle ≥ 9.5.0) или Android Studio Quail 4+.
* (−) Нужен JDK 21 на машине разработчика (sdkman) и в CI.
* PRD: обновить NFR-1 (37), §9.3 (добавить эмулятор API 37), §6.1 (выбор Navigation — ADR в M2).

## Related
NFR-1, NFR-4, NFR-6, NFR-7, PRD §6.1, §6.2, §8 (M0), §11.1; ADR-001, ADR-002.
