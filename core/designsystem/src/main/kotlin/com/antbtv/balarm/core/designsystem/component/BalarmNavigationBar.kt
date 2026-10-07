package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentLightPreview
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.preview.PreviewNavItems
import com.antbtv.balarm.core.designsystem.preview.PreviewNavItemsRu
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Нижняя панель навигации (PRD §4.1, ADR-014 §4): Material 3 `NavigationBar` с токенами Balarm. Фон `surface`,
 * выбранная вкладка — иконка и подпись `primary` + подложка-индикатор `surfaceVariant` + жирная подпись (не только
 * цвет — WCAG 1.4.1), невыбранные — `textSecondary`. Подписи видны всегда.
 *
 * TalkBack: каждая вкладка — узел с ролью «вкладка», подписью и состоянием «выбрано».
 *
 * @param selectedIndex индекс выбранной вкладки; вне диапазона — ничего не выбрано.
 * @param onSelect тап по вкладке (в т.ч. по уже выбранной — `:app` снимает её стек до корня).
 * @param windowInsets по умолчанию панель сама отступает от системной навигации (ADR-014 §4: `:app` помечает
 * `navigationBars` потреблёнными для корневых экранов).
 */
@Composable
fun BalarmNavigationBar(
    items: List<NavBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.primary,
        indicatorColor = colors.surfaceVariant,
        unselectedIconColor = colors.textSecondary,
        unselectedTextColor = colors.textSecondary,
    )
    NavigationBar(
        modifier = modifier.testTag(NavigationBarTestTags.BAR),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        windowInsets = windowInsets,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = null,
                        modifier = Modifier.size(BalarmDimens.Icon),
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = if (selected) type.captionStrong else type.caption,
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier.testTag(NavigationBarTestTags.item(index)),
                colors = itemColors,
            )
        }
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun BalarmNavigationBarPreview() {
    BalarmTheme {
        Column(verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap)) {
            BalarmNavigationBar(items = PreviewNavItems, selectedIndex = 0, onSelect = {})
            BalarmNavigationBar(items = PreviewNavItemsRu, selectedIndex = 1, onSelect = {})
        }
    }
}

@BalarmComponentLightPreview
@Composable
private fun BalarmNavigationBarLightPreview() {
    BalarmTheme(darkTheme = false) {
        BalarmNavigationBar(items = PreviewNavItemsRu, selectedIndex = 0, onSelect = {})
    }
}
