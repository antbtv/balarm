package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import kotlin.math.ceil

/** Колесо ужимает шрифт, чтобы влезть по ширине, но не мельче этой доли (дальше пусть обрезает родитель). */
private const val MIN_FIT_FACTOR = 0.5f

/** Разделитель часов и минут: пунктуация формата времени, не перевод. */
private const val TIME_SEPARATOR = ":"

/** Образец ширины двух цифр (`tnum` — все цифры одной ширины). */
private const val DIGITS_SAMPLE = "00"

/** Число колонок в 12h: часы, минуты, AM/PM. */
private const val COLUMNS_12H = 3

/**
 * Колесо выбора времени (FR-EDIT-1): «бесконечные» колонки часов и минут, в 12-часовом формате — ещё AM/PM.
 * Snap к центральной строке, haptic на каждом шаге, выбранная строка — на плашке `surfaceVariant`.
 *
 * Управляемый компонент: значение всегда 24-часовое ([hour] 0..23, [minute] 0..59), 12h — только отображение
 * (часы 12, 1…11 и AM/PM; переход 11 ↔ 12 половину суток не переключает). [onTimeChange] вызывается сразу,
 * как через центр прошло новое значение (во время прокрутки, не после остановки): «Сохранить» посреди fling
 * берёт то, что пользователь видит в центре. Вызывающий обязан вернуть новое значение в [hour]/[minute].
 * Смена [hour]/[minute] снаружи мгновенно прокручивает колесо без haptic и без [onTimeChange].
 *
 * TalkBack: каждая колонка — один регулируемый узел (имя — [hoursLabel]/[minutesLabel]/[periodLabel],
 * состояние — «07»/«PM»), листается жестами вверх/вниз (`SetProgress`) и действиями [increaseLabel]/[decreaseLabel].
 * Строки не содержит: все подписи — от вызывающего (ресурсы feature-модуля).
 *
 * При `fontScale ≥ 1.5` видно 3 строки вместо 5; если колонки не влезают по ширине, шрифт ужимается.
 * Раскладка «часы:минуты» всегда слева направо (как время в RTL-локалях).
 *
 * @param hour час 0..23.
 * @param minute минута 0..59.
 * @param onTimeChange новое время (час 0..23, минута 0..59).
 * @param is24Hour 24-часовой формат (системная настройка, `:core:format`).
 * @param hoursLabel имя колонки часов для TalkBack («Часы»).
 * @param minutesLabel имя колонки минут для TalkBack («Минуты»).
 * @param periodLabel имя колонки AM/PM для TalkBack («До или после полудня»).
 * @param amLabel подпись AM (`DateFormatSymbols.amPmStrings`).
 * @param pmLabel подпись PM.
 * @param increaseLabel действие TalkBack «Больше».
 * @param decreaseLabel действие TalkBack «Меньше».
 */
@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    is24Hour: Boolean,
    hoursLabel: String,
    minutesLabel: String,
    periodLabel: String,
    amLabel: String,
    pmLabel: String,
    increaseLabel: String,
    decreaseLabel: String,
    modifier: Modifier = Modifier,
) {
    val hour24 = hour.coerceIn(0, HOURS_24 - 1)
    val minuteValue = minute.coerceIn(0, MINUTES_IN_HOUR - 1)
    val callbacks = rememberTimeColumnCallbacks(hour24, minuteValue, is24Hour, onTimeChange)
    val names = remember(hoursLabel, minutesLabel, periodLabel) {
        TimeColumnNames(hoursLabel, minutesLabel, periodLabel)
    }
    val actionLabels = remember(increaseLabel, decreaseLabel) { WheelActionLabels(increaseLabel, decreaseLabel) }
    BoxWithConstraints(modifier = modifier.testTag(TimeWheelPickerTestTags.PICKER)) {
        val layout = rememberWheelLayout(constraints, is24Hour, amLabel, pmLabel)
        // Время читается слева направо и в RTL-локалях (как системный TimePicker).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            TimeWheelColumns(
                hour24 = hour24,
                minute = minuteValue,
                is24Hour = is24Hour,
                amLabel = amLabel,
                pmLabel = pmLabel,
                layout = layout,
                callbacks = callbacks,
                names = names,
                actionLabels = actionLabels,
            )
        }
    }
}

/** Имена колонок для TalkBack. */
@Immutable
private class TimeColumnNames(val hours: String, val minutes: String, val period: String)

/** Колбэки колонок: значение колонки → новое 24-часовое время. */
private class TimeColumnCallbacks(val onHour: (Int) -> Unit, val onMinute: (Int) -> Unit, val onPeriod: (Int) -> Unit)

/** Время, известное колесу между рекомпозициями. Не состояние Compose: читается только в колбэках. */
private class LatestTime(var hour: Int, var minute: Int)

@Composable
private fun rememberTimeColumnCallbacks(
    hour24: Int,
    minute: Int,
    is24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
): TimeColumnCallbacks {
    // Последнее известное время: обновляется и внешним значением, и своими колбэками (до рекомпозиции родителя),
    // чтобы одновременная прокрутка двух колонок не затёрла одна другую устаревшим значением.
    val latest = remember { LatestTime(hour24, minute) }
    SideEffect {
        latest.hour = hour24
        latest.minute = minute
    }
    val currentOnTimeChange by rememberUpdatedState(onTimeChange)
    val currentIs24Hour by rememberUpdatedState(is24Hour)
    return remember(latest) {
        fun emit() = currentOnTimeChange(latest.hour, latest.minute)
        TimeColumnCallbacks(
            onHour = { value ->
                latest.hour = hourFromColumn(value, latest.hour, currentIs24Hour)
                emit()
            },
            onMinute = { value ->
                latest.minute = value
                emit()
            },
            onPeriod = { value ->
                latest.hour = hourFromPeriod(value, latest.hour)
                emit()
            },
        )
    }
}

@Composable
private fun TimeWheelColumns(
    hour24: Int,
    minute: Int,
    is24Hour: Boolean,
    amLabel: String,
    pmLabel: String,
    layout: WheelLayout,
    callbacks: TimeColumnCallbacks,
    names: TimeColumnNames,
    actionLabels: WheelActionLabels,
) {
    val hourLabels = remember(is24Hour) {
        if (is24Hour) List(HOURS_24, ::twoDigits) else List(HOURS_12) { to12h(it).toString() }
    }
    val minuteLabels = remember { List(MINUTES_IN_HOUR, ::twoDigits) }
    val periodLabels = remember(amLabel, pmLabel) { listOf(amLabel, pmLabel) }
    val plaqueColor = BalarmTheme.colors.surfaceVariant
    val density = LocalDensity.current
    val rowHeightPx = with(density) { layout.geometry.rowHeight.toPx() }
    val plaqueRadiusPx = with(density) { BalarmDimens.ButtonRadius.toPx() }
    Row(
        modifier = Modifier.drawBehind { drawSelectionPlaque(plaqueColor, rowHeightPx, plaqueRadiusPx) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Смена формата меняет число значений колонки часов — состояние прокрутки создаётся заново.
        key(is24Hour) {
            WheelColumn(
                labels = hourLabels,
                value = hourColumnValue(hour24, is24Hour),
                infinite = true,
                onValueChange = callbacks.onHour,
                textStyle = layout.digitStyle,
                width = layout.digitColumnWidth,
                geometry = layout.geometry,
                name = names.hours,
                actionLabels = actionLabels,
                modifier = Modifier.testTag(TimeWheelPickerTestTags.HOURS),
            )
        }
        TimeSeparator(style = layout.digitStyle, width = layout.separatorWidth)
        WheelColumn(
            labels = minuteLabels,
            value = minute,
            infinite = true,
            onValueChange = callbacks.onMinute,
            textStyle = layout.digitStyle,
            width = layout.digitColumnWidth,
            geometry = layout.geometry,
            name = names.minutes,
            actionLabels = actionLabels,
            modifier = Modifier.testTag(TimeWheelPickerTestTags.MINUTES),
        )
        if (!is24Hour) {
            WheelColumn(
                labels = periodLabels,
                value = if (isPm(hour24)) PERIOD_PM else PERIOD_AM,
                infinite = false,
                onValueChange = callbacks.onPeriod,
                textStyle = layout.periodStyle,
                width = layout.periodColumnWidth,
                geometry = layout.geometry,
                name = names.period,
                actionLabels = actionLabels,
                modifier = Modifier
                    .padding(start = BalarmDimens.TimeWheelPeriodGap)
                    .testTag(TimeWheelPickerTestTags.PERIOD),
            )
        }
    }
}

/** «:» между часами и минутами — только визуал. */
@Composable
private fun TimeSeparator(style: TextStyle, width: Dp) {
    Text(
        text = TIME_SEPARATOR,
        style = style,
        color = BalarmTheme.colors.textPrimary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .width(width)
            .clearAndSetSemantics {},
    )
}

/** Плашка выбранной строки через всю ширину колеса, по центру по вертикали. */
private fun DrawScope.drawSelectionPlaque(color: Color, rowHeightPx: Float, radiusPx: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, (size.height - rowHeightPx) / 2),
        size = Size(size.width, rowHeightPx),
        cornerRadius = CornerRadius(radiusPx),
    )
}

/** Размеры колеса: стили цифр/AM-PM (ужатые, если не влезают по ширине), ширины колонок, высота строки. */
private class WheelLayout(
    val digitStyle: TextStyle,
    val periodStyle: TextStyle,
    val digitColumnWidth: Dp,
    val separatorWidth: Dp,
    val periodColumnWidth: Dp,
    val geometry: WheelGeometry,
)

@Composable
private fun rememberWheelLayout(
    constraints: Constraints,
    is24Hour: Boolean,
    amLabel: String,
    pmLabel: String,
): WheelLayout {
    val type = BalarmTheme.typography
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
    return remember(measurer, density, type, maxWidth, is24Hour, amLabel, pmLabel) {
        fun TextStyle.width(text: String): Int = if (text.isEmpty()) 0 else measurer.measure(text, this).size.width
        fun textWidth(digit: TextStyle, period: TextStyle): Int {
            val periodWidth = if (is24Hour) 0 else maxOf(period.width(amLabel), period.width(pmLabel))
            return 2 * digit.width(DIGITS_SAMPLE) + digit.width(TIME_SEPARATOR) + periodWidth
        }
        val padPx = with(density) { BalarmDimens.TimeWheelColumnPadding.toPx() }
        val columns = if (is24Hour) 2 else COLUMNS_12H
        val gapPx = if (is24Hour) 0f else with(density) { BalarmDimens.TimeWheelPeriodGap.toPx() }
        val fixedPx = columns * 2 * padPx + gapPx
        val baseText = textWidth(type.timeLarge, type.title)
        val factor = if (maxWidth == Constraints.Infinity || baseText == 0) {
            1f
        } else {
            ((maxWidth - fixedPx) / baseText).coerceIn(MIN_FIT_FACTOR, 1f)
        }
        val digit = type.timeLarge.scaled(factor)
        val period = type.title.scaled(factor)
        with(density) {
            val periodTextPx = if (is24Hour) 0 else maxOf(period.width(amLabel), period.width(pmLabel))
            WheelLayout(
                digitStyle = digit,
                periodStyle = period,
                digitColumnWidth = ceilToDp(digit.width(DIGITS_SAMPLE) + 2 * padPx),
                separatorWidth = ceilToDp(digit.width(TIME_SEPARATOR).toFloat()),
                periodColumnWidth = ceilToDp(periodTextPx + 2 * padPx),
                geometry = WheelGeometry(
                    rowHeight = digit.lineHeight.toDp().coerceAtLeast(BalarmDimens.MinTouch),
                    visibleRows = visibleRowCount(fontScale),
                ),
            )
        }
    }
}

private fun TextStyle.scaled(factor: Float): TextStyle =
    if (factor >= 1f) this else copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

private fun Density.ceilToDp(px: Float): Dp = ceil(px).toDp()

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@Composable
private fun PreviewTimeWheel(is24Hour: Boolean, initialHour: Int, initialMinute: Int) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    TimeWheelPicker(
        hour = hour,
        minute = minute,
        onTimeChange = { h, m ->
            hour = h
            minute = m
        },
        is24Hour = is24Hour,
        hoursLabel = "Hours",
        minutesLabel = "Minutes",
        periodLabel = "AM or PM",
        amLabel = "AM",
        pmLabel = "PM",
        increaseLabel = "Increase",
        decreaseLabel = "Decrease",
    )
}

@BalarmComponentPreviews
@Composable
private fun TimeWheelPicker24hPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewTimeWheel(is24Hour = true, initialHour = 7, initialMinute = 30)
        }
    }
}

@BalarmComponentPreviews
@Composable
private fun TimeWheelPicker12hPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewTimeWheel(is24Hour = false, initialHour = 23, initialMinute = 5)
        }
    }
}
