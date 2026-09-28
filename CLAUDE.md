# Balarm — Android-будильник с миссиями (в стиле Alarmy)

## Документы
* `docs/PRD.md` — требования (FR-*, NFR-*), архитектура (§6), этапы (§8), тест-матрица надёжности (§9.2, R1–R18).
* `docs/research/` — анализ конкурентов и отбор функций (MoSCoW).
* `docs/adr/` — архитектурные решения. `docs/milestones/` — планы этапов.
* `docs/MCP.md` — MCP-серверы и предусловия (Android SDK, adb).

## Процесс работы
1. Каждый этап начинается с `/plan-milestone M<N>` → план в `docs/milestones/` → **утверждение пользователем** → только потом код.
2. Задачи выполняются через `/implement-task <ID>`.
3. Агенты: `architect` (проектирование, ADR), `tester` (тесты, сценарии на эмуляторе), `reviewer` (ревью дифа перед коммитом).
4. Скиллы: `plan-milestone`, `implement-task`, `verify-alarm-reliability`, `add-mission`, `alarmy-ui`.
5. Коммиты — только по просьбе пользователя, формат `M<N>-T<NN>: описание`.

## Стек и соглашения
Kotlin · Jetpack Compose + Material 3 · Hilt · Room · DataStore · Coroutines/Flow · kotlinx.serialization · java.time. minSdk 26, targetSdk 36. Многомодульность по PRD §6.2, version catalog `gradle/libs.versions.toml`.
* UI: UDF/MVVM, immutable `UiState`, токены только из `:core:designsystem`, строки RU+EN.
* `:core:model` и `:core:domain` — без Android-зависимостей.
* Время — через инжектируемый `java.time.Clock`.
* Тесты: JUnit4 + Truth + Turbine + MockK, Robolectric, Compose UI Test. Без `Thread.sleep`.

## Главный инвариант — будильник срабатывает всегда
* Только `AlarmManager.setAlarmClock()`.
* Всё для звонка — в device-protected storage; цепочка звонка `directBootAware` (перезагрузка ночью до разблокировки).
* Перепланирование на boot / locked boot / time / timezone / package replaced / смену exact-alarm разрешения.
* Звук — в foreground service, не в Activity. Миссию нельзя обойти Back/Home/кнопками громкости.

## Команды
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint detekt ktlintCheck
./gradlew :app:installDebug
adb shell dumpsys alarm | grep -A8 <applicationId>
```
