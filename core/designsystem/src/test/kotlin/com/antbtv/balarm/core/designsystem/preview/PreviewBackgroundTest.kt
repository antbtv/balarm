package com.antbtv.balarm.core.designsystem.preview

import androidx.compose.ui.graphics.Color
import com.antbtv.balarm.core.designsystem.theme.DarkBalarmColors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewBackgroundTest {

    @Test
    fun `preview background matches the dark background token`() {
        assertThat(Color(PREVIEW_BACKGROUND)).isEqualTo(DarkBalarmColors.background)
    }
}
