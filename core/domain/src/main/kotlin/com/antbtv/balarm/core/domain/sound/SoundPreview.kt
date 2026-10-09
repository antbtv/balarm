package com.antbtv.balarm.core.domain.sound

import com.antbtv.balarm.core.model.SoundSettings

/** Прослушивание мелодии в редакторе и пикере; во время звонка не играет (ADR-017). */
interface SoundPreview {
    fun play(settings: SoundSettings)

    fun stop()
}
