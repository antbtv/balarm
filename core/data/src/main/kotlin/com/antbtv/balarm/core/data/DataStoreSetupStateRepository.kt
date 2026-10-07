package com.antbtv.balarm.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.antbtv.balarm.core.domain.health.SetupState
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * «Онбординг пройден» и подтверждение OEM-настроек (ADR-013). DataStore лежит в device-protected storage
 * (`DataModule`), чтобы чтение не зависело от разблокировки. Ошибка чтения → [SetupState] по умолчанию:
 * онбординг покажется ещё раз, это безопасно.
 */
@Singleton
class DataStoreSetupStateRepository @Inject constructor(private val store: DataStore<Preferences>) :
    SetupStateRepository {

    override val state: Flow<SetupState> = store.data
        .map { prefs ->
            SetupState(
                onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
                oemBackgroundConfirmed = prefs[OEM_BACKGROUND_CONFIRMED] ?: false,
            )
        }
        .catch { e -> if (e is IOException) emit(SetupState()) else throw e }

    override suspend fun completeOnboarding() {
        store.edit { it[ONBOARDING_COMPLETED] = true }
    }

    override suspend fun setOemBackgroundConfirmed(confirmed: Boolean) {
        store.edit { it[OEM_BACKGROUND_CONFIRMED] = confirmed }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val OEM_BACKGROUND_CONFIRMED = booleanPreferencesKey("oem_background_confirmed")
    }
}
