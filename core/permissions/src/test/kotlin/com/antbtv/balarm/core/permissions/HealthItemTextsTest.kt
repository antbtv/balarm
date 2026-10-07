package com.antbtv.balarm.core.permissions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.health.HealthItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class HealthItemTextsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every item has a title and an explanation`() {
        HealthItem.entries.forEach {
            assertThat(context.getString(it.titleRes)).isNotEmpty()
            assertThat(context.getString(it.whyRes)).isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "ru")
    fun `russian texts differ from english`() {
        HealthItem.entries.forEach {
            assertThat(context.getString(it.titleRes)).containsMatch("[А-Яа-я]")
        }
    }
}
