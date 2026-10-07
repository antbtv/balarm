package com.antbtv.balarm.core.designsystem.preview

import androidx.compose.ui.graphics.Color
import com.antbtv.balarm.core.designsystem.theme.DarkBalarmColors
import com.antbtv.balarm.core.designsystem.theme.LightBalarmColors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewBackgroundTest {

    @Test
    fun `preview background matches the dark background token`() {
        assertThat(Color(PREVIEW_BACKGROUND)).isEqualTo(DarkBalarmColors.background)
    }

    @Test
    fun `light preview background matches the light background token`() {
        assertThat(Color(PREVIEW_BACKGROUND_LIGHT)).isEqualTo(LightBalarmColors.background)
    }
}
