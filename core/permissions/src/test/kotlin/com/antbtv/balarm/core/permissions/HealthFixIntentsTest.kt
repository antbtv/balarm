package com.antbtv.balarm.core.permissions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.notification.AlarmNotificationChannels
import com.antbtv.balarm.core.domain.health.HealthItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthFixIntentsTest {

    private val pkg = "com.antbtv.balarm"

    private fun actions(item: HealthItem, channelOnly: Boolean = false) =
        healthFixIntents(item, pkg, channelOnly).map { it.action }

    @Test
    fun `every item except scheduling ends with the app details fallback`() {
        HealthItem.entries.filter { it != HealthItem.SCHEDULING }.forEach { item ->
            val last = healthFixIntents(item, pkg).last()
            assertThat(last.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            assertThat(last.data.toString()).isEqualTo("package:$pkg")
        }
    }

    @Test
    fun `scheduling has no system screen`() {
        assertThat(healthFixIntents(HealthItem.SCHEDULING, pkg)).isEmpty()
    }

    @Test
    fun `permission screens target the package`() {
        mapOf(
            HealthItem.EXACT_ALARMS to Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            HealthItem.FULL_SCREEN_INTENT to Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            HealthItem.OVERLAY to Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            HealthItem.BATTERY_OPTIMIZATION to Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        ).forEach { (item, action) ->
            val first = healthFixIntents(item, pkg).first()
            assertThat(first.action).isEqualTo(action)
            assertThat(first.data.toString()).isEqualTo("package:$pkg")
        }
    }

    @Test
    fun `battery has a fallback without the special permission`() {
        assertThat(actions(HealthItem.BATTERY_OPTIMIZATION)).containsAtLeast(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
        ).inOrder()
    }

    @Test
    fun `notifications lead to the app screen or to the ringing channel`() {
        val app = healthFixIntents(HealthItem.NOTIFICATIONS, pkg).first()
        assertThat(app.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(app.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(pkg)

        val channel = healthFixIntents(HealthItem.NOTIFICATIONS, pkg, channelOnly = true).first()
        assertThat(channel.action).isEqualTo(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        assertThat(channel.getStringExtra(Settings.EXTRA_CHANNEL_ID)).isEqualTo(AlarmNotificationChannels.RINGING)
    }

    @Test
    fun `background restriction and oem open the app details`() {
        assertThat(
            actions(HealthItem.BACKGROUND_RESTRICTION),
        ).containsExactly(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(actions(HealthItem.OEM_BACKGROUND)).containsExactly(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    @Test
    fun `launchFirst skips intents nobody handles`() {
        val launched = mutableListOf<String?>()
        val intents = listOf(Intent("a"), Intent("b"), Intent("c"))

        val ok = launchFirst(intents) {
            if (it.action != "c") throw ActivityNotFoundException()
            launched += it.action
        }

        assertThat(ok).isTrue()
        assertThat(launched).containsExactly("c")
    }

    @Test
    fun `launchFirst reports failure when nothing is handled`() {
        assertThat(launchFirst(listOf(Intent("a"), Intent("b"))) { throw SecurityException() }).isFalse()
        assertThat(launchFirst(emptyList()) { error("unreachable") }).isFalse()
    }
}
