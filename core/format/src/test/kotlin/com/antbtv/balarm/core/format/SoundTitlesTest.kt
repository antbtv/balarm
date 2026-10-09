package com.antbtv.balarm.core.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.model.BuiltinSound
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SoundTitlesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every builtin sound has a non-blank unique english title`() {
        val titles = BuiltinSound.entries.map { context.getString(it.titleRes()) }

        assertThat(titles).containsNoDuplicates()
        titles.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    @Config(qualifiers = "ru")
    fun `every builtin sound has a russian title`() {
        val titles = BuiltinSound.entries.map { context.getString(it.titleRes()) }

        assertThat(titles).containsNoDuplicates()
        titles.forEach { assertThat(it).containsMatch("[а-яА-Я]|8-бит") }
    }
}
