package io.homeassistant.companion.android.onboarding.authentication

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface AuthenticationModule {

    @Binds
    fun authenticationPresenter(authenticationPresenterImpl: AuthenticationPresenterImpl): AuthenticationPresenter
}
