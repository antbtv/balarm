package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    /** Точка-индикатор под выбранным днём в `DayPillsRow` (не только цвет — WCAG 1.4.1). */
    val DayPillIndicator = 4.dp

    /**
     * Нижняя граница autoSize подписи дня в `DayPillsRow`. Масштабируется `fontScale` (при 2f — как 20sp),
     * так что «Пн»…«Вс» жирным влезают в ячейку ≈ 29dp карточки на экране 360dp.
     */
    val DayPillLabelMinFontSize = 10.sp

    /** Строка дней не растягивается на планшете/в ландшафте шире этого значения. */
    val DayPillsRowMaxWidth = 280.dp

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
