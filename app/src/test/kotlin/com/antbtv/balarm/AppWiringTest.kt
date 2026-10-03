package com.antbtv.balarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.alarm.AlarmReceiver
import com.antbtv.balarm.core.alarm.RescheduleReceiver
import com.antbtv.balarm.core.alarm.ring.RingingService
import com.antbtv.balarm.feature.ringing.RingingActivity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Итоговый манифест и ресурсы `:app` после подключения цепочки звонка (ADR-001, ADR-005, ADR-007). */
@RunWith(AndroidJUnit4::class)
class AppWiringTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager

    @Test
    fun `ui intents point to the ringing screen and the main screen in a new task`() {
        val intents = AppUiIntents(context)

        assertThat(intents.ringingScreen().component).isEqualTo(ComponentName(context, RingingActivity::class.java))
        assertThat(intents.ringingScreen().flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(intents.alarmList().component).isEqualTo(ComponentName(context, MainActivity::class.java))
    }

    @Test
    @Suppress("DEPRECATION") // Robolectric 4.17 не реализует перегрузки с ComponentInfoFlags для activities
    fun `whole alarm chain works before unlock and only the launcher is exported`() {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE or
            PackageManager.MATCH_DISABLED_COMPONENTS
        val info = pm.getPackageInfo(context.packageName, flags)
        val all: List<ComponentInfo> =
            info.activities.orEmpty().toList() + info.receivers.orEmpty() + info.services.orEmpty()
        val ours = all.filter { it.name.startsWith("com.antbtv.balarm") }
        // Debug-инструменты экспортированы для adb; в release их нет — проверяет check-permissions.sh.
        val exportedAllowed = setOf(
            MainActivity::class.java.name,
            "com.antbtv.balarm.debug.FeatureFlagsActivity",
            "com.antbtv.balarm.debug.DebugAlarmReceiver",
        )

        ours.filter { it.name !in exportedAllowed }.forEach {
            assertWithMessage(it.name).that(it.exported).isFalse()
        }
        val chain = listOf(
            AlarmReceiver::class,
            RescheduleReceiver::class,
            RingingService::class,
            RingingActivity::class,
        )
            .map { it.java.name }
        chain.forEach { name ->
            assertWithMessage(name).that(ours.single { it.name == name }.directBootAware).isTrue()
        }
        val service = info.services.orEmpty().single { it.name == RingingService::class.java.name }
        assertThat(service.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
    }

    @Test
    fun `backup and device transfer exclude every domain`() {
        val domains = listOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
        val excluded = mutableMapOf<String, MutableSet<String>>()
        var section = ""
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name

                "exclude" -> if (parser.getAttributeValue(null, "path") == ".") {
                    excluded.getOrPut(section) { mutableSetOf() } += parser.getAttributeValue(null, "domain")
                }

                "include" -> error("ADR-005: до M8 ничего не включается в бэкап")
            }
        }

        assertThat(excluded.keys).containsExactly("cloud-backup", "device-transfer")
        excluded.values.forEach { assertThat(it).containsExactlyElementsIn(domains) }
    }
}
