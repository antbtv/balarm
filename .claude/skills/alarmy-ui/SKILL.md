---
name: alarmy-ui
description: Дизайн-система Balarm в стиле Alarmy на Jetpack Compose — цвета, типографика, формы, готовые компоненты (карточка будильника, колесо времени, чипы дней, большие кнопки, экран звонка). Используй при создании или изменении любого экрана/компонента UI.
---

# alarmy-ui

Стиль: тёмный, контрастный, «крупно и смело». Вдохновение — Alarmy; **не копировать** их логотип, иконки, иллюстрации, звуки, тексты.

## Токены (живут в `:core:designsystem`, источник — PRD §4.2)
```kotlin
object BalarmColors {            // тёмная тема (по умолчанию)
    val Background = Color(0xFF0E0F14)
    val Surface = Color(0xFF1B1D26)
    val SurfaceVariant = Color(0xFF262936)
    val Primary = Color(0xFFFF4D4F)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFF8B8FA3)
    // Светлая тема: Primary #D32F2F, TextSecondary #646879 (контраст ≥ 4.5:1).
    // Текст на красном в тёмной теме — только ≥ 19sp Bold (buttonLarge): #FF4D4F с белым = 3.27:1.
    // Success/Warning — только иконки и крупные элементы, не мелкий текст.
    val Success = Color(0xFF3DD68C)
    val Warning = Color(0xFFFFB020)
}
object BalarmDimens {
    val CardRadius = 20.dp; val ButtonRadius = 16.dp; val ButtonHeight = 56.dp
    val Fab = 64.dp; val ScreenPadding = 20.dp; val CardGap = 12.dp; val MinTouch = 48.dp
}
```
Типографика: `timeHuge` 96sp Bold (экран звонка), `timeLarge` 44sp Bold (карточка), `title` 22sp SemiBold, `body` 16sp, `caption` 13sp. Для цифр времени — `fontFeatureSettings = "tnum"` (цифры не прыгают).

Правила:
* Никаких захардкоженных цветов/размеров в feature-модулях — только `MaterialTheme.colorScheme` / `BalarmTheme.*`.
* Все строки — `stringResource`, RU + EN.
* Иконки — Material Symbols (Rounded).
* Каждый интерактивный элемент: `contentDescription` или `semantics`, `Modifier.testTag`.

## Компоненты `:core:designsystem`
| Компонент | Описание |
|---|---|
| `AlarmCard` | Surface, radius 20; слева время `timeLarge` (серое, если выключен), под ним метка + иконки миссий; снизу `DayChipsRow` (read-only); справа `BalarmSwitch` |
| `BalarmSwitch` | Трек Primary при вкл, SurfaceVariant при выкл |
| `TimeWheelPicker` | Два бесконечных колеса (часы/минуты) + AM/PM при 12h; выделенная строка — Surface-плашка; haptic `TextHandleMove` на каждом шаге; snap-fling |
| `DayChipsRow` | 7 круглых чипов 40dp, первый день недели — из настроек; активный = Primary фон |
| `PresetChips` | «Будни / Выходные / Каждый день» |
| `SettingRow` | Иконка, заголовок, значение справа серым, chevron; вся строка кликабельна, высота ≥ 56dp |
| `PrimaryButton` | Высота 56, radius 16, Primary; вариант `pulsing = true` для «Отключить» (scale 1.0↔1.05, 1.2 с) |
| `SecondaryButton` | SurfaceVariant фон |
| `BalarmFab` | 64dp круг Primary, иконка `add` |
| `NextAlarmHeader` | «Следующий будильник через 7 ч 12 мин», title + caption |
| `PermissionBanner` | Warning-фон 15 %, иконка, текст, кнопка «Исправить» |
| `MissionProgressBar` | Сегментированный прогресс «2/5» наверху экрана миссии + полоска таймера бездействия |
| `ShakeOnError` | Modifier: горизонтальная тряска 300 мс + haptic `Reject` |
| `QuoteBlock` | Текст курсивом 18sp, автор caption справа, опционально кнопка ♥ |

## Экраны — ориентиры компоновки
* **Список:** `NextAlarmHeader` → `LazyColumn` карточек (gap 12) → FAB справа снизу над bottom bar.
* **Редактор:** `TimeWheelPicker` сверху (≈ 40 % высоты) → `PresetChips` + `DayChipsRow` → группы `SettingRow` (Миссия, Звук, Snooze, Цитата, Метка) → закреплённая снизу `PrimaryButton` «Сохранить».
* **Звонок:** фон Background (опционально мягкий радиальный градиент Primary 8 %) → дата caption → время `timeHuge` → метка → `QuoteBlock` → снизу `SecondaryButton` «Отложить (N)» и `PrimaryButton(pulsing)` «Отключить». Системные бары скрыты, `keepScreenOn`.
* **Миссия:** `MissionProgressBar` → контент миссии по центру → своя клавиатура/кнопки внизу (крупные, 64dp).

## Проверка
* Превью `@Preview` для каждого компонента в тёмной и светлой теме и с `fontScale = 2f`.
* (Если подключены) скриншот-тесты Roborazzi для компонентов.
