package com.antbtv.balarm

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * onCreate должен оставаться безопасным для Direct Boot (ADR-001):
 * никакого доступа к credential-encrypted storage при старте процесса.
 */
@HiltAndroidApp
class BalarmApplication : Application()
