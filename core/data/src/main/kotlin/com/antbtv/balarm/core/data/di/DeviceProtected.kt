package com.antbtv.balarm.core.data.di

import javax.inject.Qualifier

/**
 * Контекст device-protected storage (ADR-001): данные доступны сразу после перезагрузки,
 * до первой разблокировки. Все файлы, БД и настройки приложения строятся только от него.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceProtected
