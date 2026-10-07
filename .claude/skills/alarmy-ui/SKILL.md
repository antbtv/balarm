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
    // + ButtonHeightLarge 64, CardPadding 16, Spacing*, DayChip 40, Icon 24, SystemBarScrimFade 16, SelectedBorder 2 …
    //   полный список — BalarmDimens / BalarmShapes в core/designsystem/theme/Dimens.kt; новые размеры — только туда.
}
```
Типографика (`BalarmTheme.typography`): `timeHuge` 96sp Bold (экран звонка), `timeLarge` 44sp Bold (карточка), `title` 22sp SemiBold, `body` 16sp, `caption` 13sp, `captionStrong` 13sp Bold (выделение в мелком тексте — выбранный день в `DayPillsRow`), `buttonLarge` 19sp Bold (текст на `primary`). Для цифр времени — `fontFeatureSettings = "tnum"` (цифры не прыгают).

Правила:
* Никаких захардкоженных цветов/размеров в feature-модулях — только `MaterialTheme.colorScheme` / `BalarmTheme.*`.
* Все строки — `stringResource`, RU + EN.
* Иконки — `BalarmIcons` (`res/drawable` в `:core:designsystem`, 24dp, авторские штриховые рисунки **CC0**: add, delete, chevron_right, keyboard; цвет — из `LocalContentColor`). Новая иконка — свой рисунок (CC0) или Material Symbols Rounded (Apache 2.0) как векторный ресурс + строка в `docs/LICENSES.md` в той же задаче. `material-icons-extended` не подключать (тяжёлый).
* Выбор не только цветом (WCAG 1.4.1): точка, обводка, жирность. Текст на `primary` — только ≥ 19sp Bold.
* Edge-to-edge: экран сам обрабатывает `WindowInsets.safeDrawing`, `:app` не оборачивает его в `Scaffold`/`padding(innerPadding)`; прокручиваемый контент под прозрачными барами — с `SystemBarScrim`.
* Каждый интерактивный элемент: `contentDescription` или `semantics`, `Modifier.testTag`.

## Компоненты `:core:designsystem`
Компоненты принимают примитивы и готовые строки (`:core:model` не знают); строки и описания TalkBack передаёт вызывающий.

| Компонент | Описание |
|---|---|
| `AlarmCard` | Surface, radius 20, отступ `CardPadding`. Верхняя строка: время `timeLarge` (серое, если неактивен) + AM/PM после цифр во всех локалях, метка, подзаголовок («Сегодня/Завтра», «Отложен до 07:05»), справа `BalarmSwitch`. Под ней **отдельной строкой во всю ширину** — `DayPillsRow`. Клик, долгий тап (меню), три обязательных описания TalkBack (`contentDescription`, `toggleDescription`, подписи действий) |
| `DayPillsRow` | Дни в карточке, **только чтение**: 7 равных ячеек, трёхбуквенные подписи из `:core:format` (`Пн`/`Mon`); выбранный — текст `primary` + `captionStrong` + точка `DayPillIndicator` (не красная заливка: 13sp белым на #FF4D4F = 3.27:1). Общий размер подписи подбирается по самой широкой (`fitDayLabelFontSize`, минимум `DayPillLabelMinSize` 10dp), ширина ≤ `DayPillsRowMaxWidth`. Принимает `List<DayPillUi>` — мемоизировать у вызывающего |
| `BalarmSwitch` | M3 `Switch` с токенами: трек Primary при вкл, SurfaceVariant при выкл |
| `TimeWheelPicker` | Управляемый: `hour/minute` (24h) + `onTimeChange` во время прокрутки; колонки — `LazyColumn` + snap, «бесконечные», 12h — 1–12 и AM/PM; выделенная строка — плашка `surfaceVariant`; haptic **`HapticFeedbackType.SegmentFrequentTick`** на каждом шаге; fontScale ≥ 1.5 → 3 строки вместо 5; TalkBack — регулируемый узел на колонку (`SetProgress`, действия «больше/меньше»); подписи колонок/AM/PM — от вызывающего. Внешняя смена значения применяется после остановки прокрутки. Центрировать снаружи |
| `DayChipsRow` | Выбор дней в редакторе: 7 круглых чипов 40dp, зона тапа — вся ячейка (1/7 ширины, ≥ 48dp, если строке дали ≥ 336dp → на 360dp горизонтальный отступ строки ≤ 12dp); порядок и подписи — у вызывающего (`WeekdayFormat`, первый день недели — `LocalePreferences.getFirstDayOfWeek()`); выбранный — заливка `primary`, текст **19sp Bold, не мельче 19sp при любом fontScale** (autosize); без иконки дня; TalkBack — флажок с «Выбрано/Не выбрано» |
| `PresetChips` | «Будни / Выходные / Каждый день» (`DayPreset`, Будни = Пн–Пт, Выходные = Сб+Вс во всех локалях); выбран, если набор дней совпадает; повторный тап очищает → разовый. Выбранный — **обводка 2dp `primary` + текст `primary`** (не заливка: 16sp на красном не проходит контраст); переносятся при fontScale 2 |
| `SettingRow` | Заголовок, значение справа `textSecondary` (≤ ½ ширины), chevron; иконка слева необязательна; вся строка кликабельна, высота ≥ 56dp; при fontScale 2 переносится по словам |
| `SingleChoiceDialog` | AlertDialog с radio-строками (≥ 56dp, `selectableGroup`); тап по варианту сразу `onSelect(index)`, закрывает вызывающий; `selectedIndex` вне диапазона — ничего не выбрано; переиспользуется (snooze M2, fade-in M4, уровень Math M5) |
| `LabelField` | Однострочное поле метки со счётчиком «12/40» в **code points** (`labelLength`); переводы строк → пробел; при превышении лимита обрезается вставленный фрагмент, суррогатные пары не рвутся |
| `ConfirmDialog` | Подтверждение (удаление, «Отменить изменения?») |
| `PrimaryButton` | Высота 56 (`ButtonHeightLarge` 64 на звонке/миссии), radius 16, Primary, текст `buttonLarge`; вариант `pulsing = true` для «Отключить» (scale 1.0↔1.05, 1.2 с) |
| `SecondaryButton` | SurfaceVariant фон («Отложить», «Тест», «Удалить») |
| `BalarmFab` | 64dp круг Primary, иконка `BalarmIcons.Add` |
| `NextAlarmHeader` | «Следующий будильник через 7 ч 12 мин» / «Нет активных будильников», `title` + подпись `caption`; TalkBack — один узел-заголовок (полная форма «через 7 часов 12 минут» — `titleDescription`) |
| `SystemBarScrim` | Подложка под прозрачным системным баром (`ScrimEdge.Top/Bottom`): сплошная `background` под баром → прозрачная за `SystemBarScrimFade`; касания не перехватывает, в дереве доступности нет. Для списка и редактора сверху и снизу |
| `HealthBanner` | (M3) Баннер FR-LIST-5: `warningContainer`, иконка, текст, подпись «Исправить», live region |
| `HealthStatusRow` | (M3) Строка ✅/⚠️/? экрана здоровья + «Исправить: <пункт>»; `HealthStatusUi`, `actionEnabled` |
| `OnboardingStepLayout` / `OnboardingIllustration` | (M3) Шаг онбординга: индикатор, иллюстрация, «зачем», основная и «Позже»; `primaryEnabled`, `warning`, слот `extraContent` |
| `BalarmNavigationBar` / `NavBarItem` | (M3) Нижняя панель на 2 вкладки; список `NavBarItem` — через `remember` |
| `BalarmTopBar` | (M3) «Назад» + заголовок для полноэкранных экранов без панели |
| `MissionProgressBar` | (M5) Сегментированный прогресс «2/5» наверху экрана миссии + полоска таймера бездействия |
| `ShakeOnError` | (M5) Modifier: горизонтальная тряска 300 мс + haptic `Reject` |

## Экраны — ориентиры компоновки
* **Список:** `LazyColumn`: первым элементом `NextAlarmHeader` (прокручивается, не закреплена) → карточки (gap 12) → FAB справа снизу над навигационной панелью (bottom bar — M3), последняя карточка FAB'ом не перекрыта; `SystemBarScrim` сверху и снизу. Во время загрузки — ни шапки, ни «пусто». **Пустое состояние:** «Пока нет будильников» + подсказка «Нажмите +, чтобы добавить первый будильник». **Долгий тап** по карточке → `DropdownMenu` «Удалить» → `ConfirmDialog` (свайп — бэклог); TalkBack-действие «Показать действия».
* **Редактор:** заголовок → `TimeWheelPicker` сверху → `PresetChips` + `DayChipsRow` → `LabelField` → секция snooze (`SettingRow` «Интервал»/«Лимит» + `SingleChoiceDialog`; скрыта при `feature.snooze=false`) → (M4: Звук, M5: Миссия — `SettingRow`) → `SecondaryButton` «Тест» и «Удалить» (только для существующего) → закреплённая снизу `PrimaryButton` «Сохранить» с градиентом над ней. Back с правками → `ConfirmDialog` «Отменить изменения?».
* **Звонок:** фон Background (опционально мягкий радиальный градиент Primary 8 %) → дата caption → время `timeHuge` → метка → снизу `SecondaryButton` «Отложить (N)» (в тестовом звонке скрыта) и `PrimaryButton(pulsing)` «Отключить». **Системные бары видимы** (прозрачные, edge-to-edge; иконки — `SystemBarStyle.dark`, при светлой теме выбирать по теме), контент ими не перекрыт; `keepScreenOn`. Для экрана миссии (M5) решение о скрытых барах — отдельное.
* **Миссия:** `MissionProgressBar` → контент миссии по центру → своя клавиатура/кнопки внизу (крупные, 64dp).

## Проверка
* Превью `@Preview` для каждого компонента: тёмная тема (светлая — бэклог, PRD §2), `fontScale = 2f`, ширина 360dp, RU и EN.
* (Если подключены) скриншот-тесты Roborazzi для компонентов.
