package com.antbtv.balarm.core.domain.sound

import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Прослушивание мелодии в редакторе, пикере и библиотеке (FR-EDIT-5, FR-SND-3). Без нарастания и резервов;
 * автостоп через 10 с; во время звонка не играет (ADR-017 §6). Вызывается только с видимого экрана.
 */
interface SoundPreview {
    /** Какая мелодия играет сейчас (`null` — тишина). */
    val playing: StateFlow<SoundRef?>

    /** Играет [SoundSettings.sound] на громкости [SoundSettings.volumePercent]; повторный вызов — перезапуск. */
    fun play(settings: SoundSettings)

    fun stop()
}
