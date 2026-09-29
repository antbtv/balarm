---
name: add-mission
description: Добавление нового типа миссии (головоломки для отключения будильника) в Balarm — Math, Memory, Typing, Shake и новые. Используй, когда нужно создать новую миссию или изменить существующую; описывает контракт, структуру модуля, генератор, UI, тесты и регистрацию.
argument-hint: "<название миссии, например shake>"
---

# add-mission

Миссия — изолированный плагин. Ядро (`:feature:ringing`, `:core:alarm`) не меняется.

## Контракт (`:feature:missions:api`)
Если контракт ещё не создан (до M5) — сначала создай его по этой схеме и согласуй с architect.
```kotlin
enum class MissionType { MATH, MEMORY, TYPING, SHAKE /* … */ }

@Serializable sealed interface MissionConfig { val type: MissionType }

interface MissionDefinition<C : MissionConfig> {
    val type: MissionType
    @get:StringRes val titleRes: Int
    val icon: ImageVector
    val defaultConfig: C
    val configSerializer: KSerializer<C>
    @Composable fun ConfigEditor(config: C, onChange: (C) -> Unit)
    @Composable fun MissionScreen(config: C, mode: MissionMode, callbacks: MissionCallbacks)
}

enum class MissionMode { RINGING, PREVIEW }

interface MissionCallbacks {
    fun onProgress(done: Int, total: Int)   // обновляет прогресс-бар хоста
    fun onUserInteraction()                 // сбрасывает таймер бездействия
    fun onCompleted()                       // миссия пройдена
}
```
Хост (`MissionHost` в `:feature:ringing`) отвечает за: прогресс-бар, таймер бездействия, громкость, блокировку Back, переход к следующей миссии. Миссия **не** трогает звук, сервис и навигацию.

## Шаги
1. **Модуль** `:feature:missions:<name>` (convention plugin `balarm.android.feature`), зависимость только на `:feature:missions:api`, `:core:designsystem`, `:core:model` (и `:core:domain`, если нужны цитаты).
2. **Config:** `@Serializable data class <Name>Config(...) : MissionConfig` с валидацией диапазонов в `init { require(...) }`. Добавь значение в `MissionType`. Изменение сериализованного формата = миграция.
3. **Логика** — чистый Kotlin-класс без Android (`<Name>Engine`/`<Name>Generator`): генерация задач с инжектируемым `Random(seed)`, проверка ответа, состояние. Всё тестируется на JVM.
4. **ViewModel/State** — `UiState` immutable; каждое действие пользователя → `callbacks.onUserInteraction()`.
5. **UI** по скиллу `alarmy-ui`: полноэкранно, крупно (использовать утром сонному человеку!), клавиатура своя где нужно, тряска + haptic при ошибке, testTag на интерактивных элементах.
6. **ConfigEditor** — для экрана настройки миссии в редакторе будильника + кнопка «Попробовать» (PREVIEW).
7. **Регистрация:** Hilt `@IntoMap @MissionTypeKey(MissionType.X)` binding `MissionDefinition` в модуле миссии; подключить модуль в `:app`.
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
