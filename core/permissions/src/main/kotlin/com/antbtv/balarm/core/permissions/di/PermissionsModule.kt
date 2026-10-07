package com.antbtv.balarm.core.permissions.di

import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.permissions.AndroidPermissionHealthChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface PermissionsModule {
    @Binds
    fun bindHealthChecker(impl: AndroidPermissionHealthChecker): PermissionHealthChecker
}
