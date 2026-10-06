package com.antbtv.balarm.feature.alarmedit

import com.antbtv.balarm.core.designsystem.component.DayPreset
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.SnoozeSettings
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import org.junit.Test

class AlarmEditReducerTest {

    private val base = Alarm(time = LocalTime.of(7, 0), snooze = SnoozeSettings.DEFAULT)

    private fun state(
        draft: Alarm = base,
        initial: Alarm = base,
        isNew: Boolean = false,
        snoozeVisible: Boolean = true,
        loading: Boolean = false,
        saving: Boolean = false,
    ) = AlarmEditUiState(
        loading = loading,
        isNew = isNew,
        initial = initial,
        draft = draft,
        snoozeVisible = snoozeVisible,
        saving = saving,
    )

    private fun reduce(state: AlarmEditUiState, vararg events: AlarmEditEvent) =
        events.fold(state) { acc, event -> AlarmEditReducer.reduce(acc, event) }

    @Test
    fun `a fresh editor is not dirty`() {
        assertThat(state().isDirty).isFalse()
    }

    @Test
    fun `time change makes it dirty and a return to the initial time does not`() {
        val changed = reduce(state(), AlarmEditEvent.TimeChanged(LocalTime.of(7, 5)))
        assertThat(changed.draft.time).isEqualTo(LocalTime.of(7, 5))
        assertThat(changed.isDirty).isTrue()

        val back = reduce(changed, AlarmEditEvent.TimeChanged(LocalTime.of(7, 0)))
        assertThat(back.isDirty).isFalse()
    }

    @Test
    fun `time is kept to whole minutes`() {
        val changed = reduce(state(), AlarmEditEvent.TimeChanged(LocalTime.of(7, 5, 30, 1)))

        assertThat(changed.draft.time).isEqualTo(LocalTime.of(7, 5))
    }

    @Test
    fun `toggling a day twice returns to a one-shot and clean state`() {
        val on = reduce(state(), AlarmEditEvent.DayToggled(DayOfWeek.MONDAY))
        assertThat(on.draft.repeatDays).containsExactly(DayOfWeek.MONDAY)
        assertThat(on.isDirty).isTrue()

        val off = reduce(on, AlarmEditEvent.DayToggled(DayOfWeek.MONDAY))
        assertThat(off.draft.isOneShot).isTrue()
        assertThat(off.isDirty).isFalse()
    }

    @Test
    fun `a preset sets its days and the second tap clears them`() {
        val weekdays = reduce(state(), AlarmEditEvent.PresetSelected(DayPreset.WEEKDAYS))
        assertThat(weekdays.draft.repeatDays).isEqualTo(DayPreset.WEEKDAYS.days)

        val cleared = reduce(weekdays, AlarmEditEvent.PresetSelected(DayPreset.WEEKDAYS))
        assertThat(cleared.draft.repeatDays).isEmpty()

        val switched = reduce(weekdays, AlarmEditEvent.PresetSelected(DayPreset.EVERY_DAY))
        assertThat(switched.draft.repeatDays).isEqualTo(DayPreset.EVERY_DAY.days)
    }

    @Test
    fun `label is cut to the limit in code points without splitting a pair`() {
        val emoji = "😀"
        val tooLong = "a".repeat(39) + emoji + emoji

        val label = reduce(state(), AlarmEditEvent.LabelChanged(tooLong)).draft.label

        assertThat(label).isEqualTo("a".repeat(39) + emoji)
        assertThat(label.codePointCount(0, label.length)).isEqualTo(Alarm.MAX_LABEL_LENGTH)
    }

    @Test
    fun `line breaks in the label become spaces`() {
        val label = reduce(state(), AlarmEditEvent.LabelChanged("a\r\nb\nc\u0085d\u2028e\u2029f")).draft.label

        assertThat(label).isEqualTo("a b c d e f")
    }

    @Test
    fun `snooze interval and limit are changed`() {
        val result = reduce(
            state(),
            AlarmEditEvent.SnoozeIntervalSelected(Duration.ofMinutes(10)),
            AlarmEditEvent.SnoozeLimitSelected(null),
        )

        assertThat(result.draft.snooze).isEqualTo(SnoozeSettings(Duration.ofMinutes(10), maxCount = null))
    }

    @Test
    fun `turning snooze off keeps the limit and turning it on again restores it`() {
        val off = reduce(state(), AlarmEditEvent.SnoozeIntervalSelected(null))
        assertThat(off.draft.snooze).isEqualTo(SnoozeSettings(interval = null, maxCount = 3))

        val on = reduce(off, AlarmEditEvent.SnoozeIntervalSelected(Duration.ofMinutes(5)))
        assertThat(on.draft.snooze).isEqualTo(SnoozeSettings.DEFAULT)
        assertThat(on.isDirty).isFalse()
    }

    @Test
    fun `enabling snooze that was stored as disabled uses the default limit, not unlimited`() {
        val disabled = base.copy(snooze = SnoozeSettings.DISABLED)

        val on = reduce(
            state(draft = disabled, initial = disabled),
            AlarmEditEvent.SnoozeIntervalSelected(Duration.ofMinutes(10)),
        )

        assertThat(on.draft.snooze).isEqualTo(SnoozeSettings(Duration.ofMinutes(10), SnoozeSettings.DEFAULT.maxCount))
    }

    @Test
    fun `the limit cannot be changed while snooze is off`() {
        val off = base.copy(snooze = SnoozeSettings.DISABLED)
        val s = state(draft = off, initial = off)

        assertThat(reduce(s, AlarmEditEvent.SnoozeLimitSelected(5)).draft.snooze).isEqualTo(SnoozeSettings.DISABLED)
        assertThat(reduce(s, AlarmEditEvent.ShowSnoozeLimitDialog).dialog).isNull()
    }

    @Test
    fun `with the snooze flag off the snooze events and dialogs do nothing`() {
        val stored = base.copy(snooze = SnoozeSettings(Duration.ofMinutes(15), maxCount = 5))
        val s = state(draft = stored, initial = stored, snoozeVisible = false)

        val result = reduce(
            s,
            AlarmEditEvent.SnoozeIntervalSelected(null),
            AlarmEditEvent.SnoozeLimitSelected(1),
            AlarmEditEvent.ShowSnoozeIntervalDialog,
            AlarmEditEvent.ShowSnoozeLimitDialog,
        )

        assertThat(result).isEqualTo(s)
    }

    @Test
    fun `snooze dialogs open and close`() {
        val interval = reduce(state(), AlarmEditEvent.ShowSnoozeIntervalDialog)
        assertThat(interval.dialog).isEqualTo(EditDialog.SnoozeInterval)
        val limit = reduce(state(), AlarmEditEvent.ShowSnoozeLimitDialog)
        assertThat(limit.dialog).isEqualTo(EditDialog.SnoozeLimit)

        assertThat(reduce(interval, AlarmEditEvent.DialogDismissed).dialog).isNull()
    }

    @Test
    fun `choosing a snooze value closes its dialog`() {
        val open = reduce(state(), AlarmEditEvent.ShowSnoozeIntervalDialog)

        val chosen = reduce(open, AlarmEditEvent.SnoozeIntervalSelected(Duration.ofMinutes(10)))

        assertThat(chosen.dialog).isNull()
    }

    @Test
    fun `delete asks for confirmation only for an existing alarm`() {
        assertThat(reduce(state(), AlarmEditEvent.Delete).dialog).isEqualTo(EditDialog.ConfirmDelete)
        assertThat(reduce(state(isNew = true), AlarmEditEvent.Delete).dialog).isNull()
    }

    @Test
    fun `back asks about discarding only when there are changes`() {
        assertThat(reduce(state(), AlarmEditEvent.Back).dialog).isNull()

        val dirty = reduce(state(), AlarmEditEvent.DayToggled(DayOfWeek.FRIDAY), AlarmEditEvent.Back)

        assertThat(dirty.dialog).isEqualTo(EditDialog.ConfirmDiscard)
    }

    @Test
    fun `delete is not offered while saving`() {
        assertThat(reduce(state(saving = true), AlarmEditEvent.Delete).dialog).isNull()
    }

    @Test
    fun `nothing changes while loading`() {
        val loading = state(loading = true)

        val result = reduce(
            loading,
            AlarmEditEvent.TimeChanged(LocalTime.of(9, 0)),
            AlarmEditEvent.LabelChanged("x"),
            AlarmEditEvent.Delete,
        )

        assertThat(result).isEqualTo(loading)
    }

    @Test
    fun `nothing can be edited while saving`() {
        val saving = state(saving = true)

        val result = reduce(saving, AlarmEditEvent.TimeChanged(LocalTime.of(9, 0)), AlarmEditEvent.LabelChanged("x"))

        assertThat(result).isEqualTo(saving)
    }
}
