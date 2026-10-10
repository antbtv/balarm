---
name: add-mission
description: Добавление нового типа миссии (головоломки для отключения будильника) в Balarm — Math, Memory, Typing, Shake и новые. Используй, когда нужно создать новую миссию или изменить существующую; описывает контракт, структуру модуля, генератор, UI, тесты и регистрацию.
argument-hint: "<название миссии, например shake>"
---

# add-mission

Миссия — изолированный плагин. Ядро (`:feature:ringing`, `:core:alarm`) не меняется.

## Контракт (`:feature:missions:api`, ADR-018)
Ядро хранит только `MissionSpec(type: MissionTypeId, config: String /* JSON */)` (`:core:model`) — новый тип миссии не требует правок ядра, enum и sealed не нужны.
```kotlin
interface MissionDefinition {
    val type: MissionTypeId
    val feature: Feature                       // флаг миссии (выключен → миссии нет, будильник отключается сразу)
    @get:StringRes val titleRes: Int
    fun defaultConfig(): String
    fun normalize(config: String): String      // чинит любой конфиг, не бросает
    fun summary(context: Context, config: String): String
    @Composable fun ConfigEditor(config: String, onChange: (String) -> Unit, modifier: Modifier)
    @Composable fun Content(config: String, session: MissionSession, modifier: Modifier)
}

class MissionSession(
    val seed: Long, val isPreview: Boolean,
    val resetSignal: Int,                      // растёт при бездействии — сбросить ввод текущей задачи
    val onInteraction: () -> Unit,             // каждое касание: сбрасывает таймер бездействия
    val onProgress: (done: Int, total: Int) -> Unit,
    val onCompleted: () -> Unit,
)
```
Хост (`MissionHost` в `:feature:ringing` и `MissionPreviewRoute` в `:feature:missions:api`) рисует `MissionFrame` (прогресс, системные панели), блокирует Back, передаёт сессию. Приглушение звука, таймер бездействия и отклонение «Отключить» — в `RingingService` (ADR-019). Миссия **не** трогает звук, сервис и навигацию.

## Шаги
1. **Модуль** `:feature:missions:<name>` (convention plugin `balarm.android.feature`), зависимость только на `:feature:missions:api`, `:core:designsystem`, `:core:model` .
2. **Config:** `data class <Name>Config(...)` с `init { require(...) }`, `encode()` в JSON и толерантный `parse(raw)` (мусор → значения по умолчанию, выход за границы → `coerceIn`; пример — `MathConfig`). Новое поле — только с значением по умолчанию.
3. **Логика** — чистый Kotlin-класс без Android (`<Name>Engine`/`<Name>Generator`): генерация задач с инжектируемым `Random(seed)`, проверка ответа, состояние. Всё тестируется на JVM.
4. **ViewModel/State** — `UiState` immutable; каждое действие пользователя → `session.onInteraction()`.
5. **UI** по скиллу `alarmy-ui`: полноэкранно, крупно (использовать утром сонному человеку!), клавиатура своя где нужно, тряска + haptic при ошибке, testTag на интерактивных элементах.
6. **ConfigEditor** — для экрана настройки миссии в редакторе будильника + кнопка «Превью» (`MissionPreviewRoute`, `isPreview = true`).
7. **Регистрация:** добавь `object <Name>Mission : MissionDefinition` в список `MissionRegistry` в `:app` (`di/MissionModule.kt`) и новый флаг `feature.mission.<name>` в `config/features.properties` (`false` до T-docs этапа). Настройка в редакторе и превью подхватываются реестром автоматически.
8. **Строки** RU + EN.
9. **Тесты:**
   * unit: генератор (детерминизм по seed, диапазоны, все уровни), проверка ответа (граничные: пусто, пробелы, регистр, отрицательные), завершение после N задач;
   * Compose UI: правильный ответ → `onCompleted`, неправильный → нет; бездействие не завершает миссию;
   * сценарии R16, R17 через `verify-alarm-reliability`.
10. **Ревью** агентом reviewer.

## Проверка перед сдачей
- [ ] Модуль не зависит от других миссий и от `:feature:ringing`.
- [ ] Миссию невозможно «проскочить» (двойной тап, поворот экрана, process death — состояние в `SavedStateHandle`).
- [ ] Работает в PREVIEW без звука.
- [ ] Шрифты/кнопки читаемы на маленьком экране (360dp) и при шрифте 200 %.
