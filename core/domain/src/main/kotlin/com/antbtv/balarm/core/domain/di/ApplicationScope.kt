package com.antbtv.balarm.core.domain.di

import javax.inject.Qualifier

/**
 * `CoroutineScope` на всё время жизни процесса: работа, которая должна пережить экран или ресивер
 * (перепланирование при запуске, `goAsync`). Предоставляется в `:app`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
