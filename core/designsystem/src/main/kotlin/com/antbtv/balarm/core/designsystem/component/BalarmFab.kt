package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Круглая кнопка «добавить»: 64dp, `primary`, иконка [BalarmIcons.Add]. */
@Composable
fun BalarmFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(BalarmDimens.Fab)
            .semantics { this.contentDescription = contentDescription },
        shape = BalarmShapes.Fab,
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
    ) {
        Icon(
            painter = painterResource(BalarmIcons.Add),
            contentDescription = null,
            modifier = Modifier.size(BalarmDimens.Icon),
        )
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun BalarmFabPreview() {
    BalarmTheme {
        Box(modifier = Modifier.padding(BalarmDimens.ScreenPadding)) {
            BalarmFab(onClick = {}, contentDescription = "Add alarm")
        }
    }
}
