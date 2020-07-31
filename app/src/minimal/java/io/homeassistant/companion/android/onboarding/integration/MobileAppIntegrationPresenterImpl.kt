package io.homeassistant.companion.android.onboarding.integration

import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject

class MobileAppIntegrationPresenterImpl @Inject constructor(
    view: MobileAppIntegrationView,
    integrationUseCase: IntegrationUseCase
) : MobileAppIntegrationPresenterBase(view, integrationUseCase)
