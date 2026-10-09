package com.antbtv.balarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.feature.alarmlist.AlarmListViewModel
import com.antbtv.balarm.feature.onboarding.OnboardingViewModel
import com.antbtv.balarm.feature.settings.HealthViewModel
import com.antbtv.balarm.feature.settings.SettingsViewModel
import com.antbtv.balarm.feature.sounds.SoundLibraryViewModel
import com.antbtv.balarm.feature.sounds.SoundPickerViewModel
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.lifecycle.withCreationCallback
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Граф Hilt приложения создаёт все ViewModel экранов со всеми зависимостями (дополняет [AppWiringTest]:
 * там манифест и ресурсы, здесь — DI). Фабрика Activity та же, что у `hiltViewModel()` в записях Nav3.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HiltGraphTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Test
    fun `every screen view model is created by the app graph`() {
        val types: List<Class<out ViewModel>> = listOf(
            AppViewModel::class.java,
            AlarmListViewModel::class.java,
            SettingsViewModel::class.java,
            HealthViewModel::class.java,
            OnboardingViewModel::class.java,
            SoundLibraryViewModel::class.java,
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val provider = ViewModelProvider(activity)
                types.forEach { type -> assertWithMessage(type.simpleName).that(provider[type]).isNotNull() }
                // Assisted-инъекция пикера — как `hiltViewModel(creationCallback = …)` в записи Nav3.
                val extras = activity.defaultViewModelCreationExtras
                    .withCreationCallback<SoundPickerViewModel.Factory> { it.create(SoundRef.DEFAULT.encode()) }
                val picker = ViewModelProvider.create(
                    activity.viewModelStore,
                    activity.defaultViewModelProviderFactory,
                    extras,
                )
                assertWithMessage("SoundPickerViewModel").that(picker[SoundPickerViewModel::class]).isNotNull()
            }
        }
    }
}
