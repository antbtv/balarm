package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.model.BuiltinSound
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Ключ в БД = имя файла в `res/raw` (ADR-016 §1): пропавший ресурс означал бы тишину вместо мелодии. */
@RunWith(AndroidJUnit4::class)
class BuiltinSoundResourcesTest {

    @Test
    fun `every builtin sound has a raw resource named by its key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        BuiltinSound.entries.forEach {
            val id = context.resources.getIdentifier(it.key, "raw", context.packageName)
            assertThat(id).isNotEqualTo(0)
        }
    }
}
