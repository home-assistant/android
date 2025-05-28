package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntegrationRequestTest {
    @Test
    fun `Given a valid IntegrationRequest when serializing then it generates a valid JSON`() {
        assertEquals(
            """{"type":"update_location","data":{}}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(UpdateLocationIntegrationRequest(UpdateLocationRequest())),
        )
        assertEquals(
            """{"type":"update_registration","data":{}}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(RegisterDeviceIntegrationRequest(RegisterDeviceRequest())),
        )
        assertEquals(
            """{"type":"register_sensor","data":{"unique_id":"42","state":null,"type":"test","icon":"test","attributes":{}}}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(RegisterSensorIntegrationRequest(SensorRegistrationRequest(uniqueId = "42", state = null, type = "test", icon = "test", attributes = emptyMap()))),
        )
        assertEquals(
            """{"type":"update_sensor_states","data":[]}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(UpdateSensorStatesIntegrationRequest(listOf())),
        )
        assertEquals(
            """{"type":"render_template","data":{}}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(RenderTemplateIntegrationRequest(emptyMap())),
        )
        assertEquals(
            """{"type":"call_service","data":{"domain":"domain","service":"service","service_data":{}}}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(CallServiceIntegrationRequest(ActionRequest(domain = "domain", service = "service", serviceData = emptyMap()))),
        )
        assertEquals(
            """{"type":"scan_tag","data":{}}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(ScanTagIntegrationRequest(emptyMap())),
        )
        assertEquals(
            """{"type":"fire_event","data":{"event_type":"type","event_data":{}}}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(FireEventIntegrationRequest(FireEventRequest("type", emptyMap()))),
        )
        assertEquals(
            """{"type":"get_zones"}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(GetZonesIntegrationRequest),
        )
        assertEquals(
            """{"type":"get_config"}""",
            kotlinJsonMapper.encodeToString<IntegrationRequest>(GetConfigIntegrationRequest),
        )
    }
}
