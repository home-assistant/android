package io.homeassistant.companion.android.onboarding.integration

import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import javax.inject.Inject

class MobileAppIntegrationPresenterImpl @Inject constructor(
    view: MobileAppIntegrationView,
    integrationUseCase: IntegrationRepository
) : MobileAppIntegrationPresenterBase(view, integrationUseCase)
