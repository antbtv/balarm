package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Размеры и отступы Balarm (PRD §4.2, скилл alarmy-ui). */
object BalarmDimens {
    val CardRadius = 20.dp
    val ButtonRadius = 16.dp
    val ButtonHeight = 56.dp

    /** Кнопки экранов звонка и миссий: сонный палец должен попасть с первого раза. */
    val ButtonHeightLarge = 64.dp
    val Fab = 64.dp
    val ScreenPadding = 20.dp
    val CardGap = 12.dp
    val MinTouch = 48.dp
    val SpacingTiny = 4.dp
    val SpacingSmall = 8.dp

    /** Внутренний отступ карточки будильника. */
    val CardPadding = 16.dp

    /**
     * Минимальный диаметр точки-индикатора под выбранным днём в `DayPillsRow` (не только цвет — WCAG 1.4.1).
     * С крупной подписью точка растёт пропорционально ей.
     */
    val DayPillIndicator = 4.dp

    /**
     * Нижняя граница размера подписи дня в `DayPillsRow` — в dp, т. е. **без** учёта `fontScale`: при
     * fontScale 2 подписи ужимаются, чтобы целиком влезть в ячейку, но не мельче этого значения.
     */
    val DayPillLabelMinSize = 10.dp

    /** Строка дней не растягивается на планшете/в ландшафте шире этого значения (7 ячеек по 48dp). */
    val DayPillsRowMaxWidth = 336.dp

    /** Плавный переход `SystemBarScrim` в прозрачный — сверх высоты самого системного бара. */
    val SystemBarScrimFade = 16.dp

    /** Размер иконок. */
    val Icon = 24.dp

    /** Горизонтальный отступ текста внутри колонки `TimeWheelPicker` (с каждой стороны). */
    val TimeWheelColumnPadding = 12.dp

    /** Зазор между колонкой минут и AM/PM в `TimeWheelPicker`. */
    val TimeWheelPeriodGap = 8.dp

    /** Видимый круг дня в `DayChipsRow` (зона тапа вокруг — ячейка ≥ [MinTouch] по высоте). */
    val DayChip = 40.dp

    /**
     * Строка `DayChipsRow` не растягивается шире: 7 ячеек по 56dp. Ячейка ≥ 48dp по ширине, если строке дали
     * ≥ 336dp (на экране 360dp — горизонтальные отступы строки ≤ 12dp).
     */
    val DayChipsRowMaxWidth = 392.dp

    /** Высота видимой «таблетки» `PresetChips` (зона тапа — ≥ [MinTouch]). */
    val PresetChipHeight = 40.dp

    /** Горизонтальный отступ текста внутри «таблетки» `PresetChips`. */
    val ChipPaddingHorizontal = 16.dp

    /** Обводка выбранного чипа (второй признак выбора, кроме цвета — WCAG 1.4.1). */
    val SelectedBorder = 2.dp

    /** Минимальная высота строки списка: `SettingRow`, варианты `SingleChoiceDialog`. */
    val ListRowMinHeight = 56.dp
}

/** Формы Balarm: кнопки — 16dp, карточки — 20dp, FAB и точки-индикаторы — круг. */
object BalarmShapes {
    val Button = RoundedCornerShape(BalarmDimens.ButtonRadius)
    val Card = RoundedCornerShape(BalarmDimens.CardRadius)
    val Fab = CircleShape

    /** Круг для мелких элементов: точка-индикатор дня, цветовые образцы. */
    val Circle = CircleShape
}

/** M3 [Shapes]: `medium` — кнопки/поля, `large` — карточки. */
internal val BalarmMaterialShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = BalarmShapes.Button,
    large = BalarmShapes.Card,
    extraLarge = RoundedCornerShape(28.dp),
)
