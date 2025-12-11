package io.homeassistant.companion.android.common.data.servers

// This should never be persisted and we should always check the info before making a call
class SecurityState(val isOnHomeNetwork: Boolean, val hasHomeSetup: Boolean, val locationEnabled: Boolean)
