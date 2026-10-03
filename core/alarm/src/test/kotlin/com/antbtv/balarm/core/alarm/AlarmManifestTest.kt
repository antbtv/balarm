package com.antbtv.balarm.core.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.ring.RingingService
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Манифест `:core:alarm` (ADR-001 §3): цепочка звонка работает до разблокировки и закрыта от чужих приложений.
 * Итоговый манифест `:app` дополнительно проверяет `scripts/check-permissions.sh`.
 */
@RunWith(AndroidJUnit4::class)
class AlarmManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager
    private val flags = PackageManager.ComponentInfoFlags.of(
        (PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE).toLong(),
    )

    @Test
    fun `alarm chain receivers are direct boot aware and not exported`() {
        listOf(AlarmReceiver::class.java, RescheduleReceiver::class.java).forEach { receiver ->
            val info = pm.getReceiverInfo(ComponentName(context, receiver), flags)
            assertWithMessage(receiver.simpleName).that(info.directBootAware).isTrue()
            assertWithMessage(receiver.simpleName).that(info.exported).isFalse()
        }
    }

    @Test
    fun `ringing service is a direct boot aware system exempted foreground service`() {
        val info = pm.getServiceInfo(ComponentName(context, RingingService::class.java), flags)

        assertThat(info.directBootAware).isTrue()
        assertThat(info.exported).isFalse()
        assertThat(info.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
    }

    @Test
    fun `all six system events reach the reschedule receiver`() {
        listOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCALE_CHANGED,
        ).forEach { action ->
            assertWithMessage(action).that(RescheduleReceiver.reasonOf(action)).isNotNull()
            val receivers = pm.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                PackageManager.ResolveInfoFlags.of(0),
            )
            assertWithMessage(action).that(receivers.map { it.activityInfo.name })
                .contains(RescheduleReceiver::class.java.name)
        }
    }

    @Test
    fun `alarm receiver has no intent filters`() {
        val receivers = pm.queryBroadcastReceivers(
            Intent(AlarmIntents.ACTION_FIRE).setPackage(context.packageName),
            PackageManager.ResolveInfoFlags.of(0),
        )
        assertThat(receivers).isEmpty()
    }

    @Test
    fun `alarm chain declares exactly the permissions it needs`() {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        val requested = pm.getPackageInfo(context.packageName, flags).requestedPermissions.orEmpty().toSet()

        assertThat(requested).containsAtLeast(
            "android.permission.USE_EXACT_ALARM",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.WAKE_LOCK",
            "android.permission.VIBRATE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            "android.permission.POST_NOTIFICATIONS",
        )
        // minSdk 34: USE_EXACT_ALARM выдаётся при установке, запрашиваемое пользователем не нужно
        assertThat(requested).doesNotContain("android.permission.SCHEDULE_EXACT_ALARM")
    }
}
