package com.spaco.app.core.data.security

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the cipher that seals the coach configuration to its Android Keystore
 * implementation, so the store itself stays free of platform crypto and can be
 * exercised with a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideCoachCredentialCipher(): CoachCredentialCipher = KeystoreCredentialCipher()
}
