package com.antbtv.balarm.debug

import android.content.Context
import android.content.Intent
import com.antbtv.balarm.DebugTools
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Debug: «О приложении» → 7 тапов по версии → экран feature flags. */
class FeatureFlagsDebugTools @Inject constructor(@ApplicationContext private val context: Context) : DebugTools {
    override fun featureFlagsIntent(): Intent = Intent(context, FeatureFlagsActivity::class.java)
}
