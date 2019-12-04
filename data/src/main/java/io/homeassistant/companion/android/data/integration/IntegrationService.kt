package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.domain.integration.EntityResponse
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface IntegrationService {

    @POST("/api/mobile_app/registrations")
    suspend fun registerDevice(
        @Header("Authorization") auth: String,
        @Body request: RegisterDeviceRequest
    ): RegisterDeviceResponse

    @POST
    suspend fun updateLocation(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Response<ResponseBody>

    //[{
// 	"entity_id": "zone.home",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.273657,
// 		"longitude": -77.351844,
// 		"radius": 60.0,
// 		"friendly_name": "Home",
// 		"icon": "mdi:home"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.342904+00:00",
// 	"last_updated": "2019-12-03T08:10:56.342904+00:00",
// 	"context": {
// 		"id": "a56d07b46f384868970d9eaa587bdb26",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.ofd_station_1",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.222727,
// 		"longitude": -77.292163,
// 		"radius": 50.0,
// 		"friendly_name": "OFD Station 1",
// 		"icon": "mdi:fire"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.345257+00:00",
// 	"last_updated": "2019-12-03T08:10:56.345257+00:00",
// 	"context": {
// 		"id": "8926005d911649e2904bde1164681365",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.ofd_station_2",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.258697,
// 		"longitude": -77.291816,
// 		"radius": 50.0,
// 		"friendly_name": "OFD Station 2",
// 		"icon": "mdi:fire"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.345533+00:00",
// 	"last_updated": "2019-12-03T08:10:56.345533+00:00",
// 	"context": {
// 		"id": "4e16fecf339949a38b535687428143a7",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.paychex_basket",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.225726,
// 		"longitude": -77.38874,
// 		"radius": 90.0,
// 		"friendly_name": "Paychex - Basket",
// 		"icon": "mdi:worker"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.343276+00:00",
// 	"last_updated": "2019-12-03T08:10:56.343276+00:00",
// 	"context": {
// 		"id": "2437b1d22613481d9107a02cc8d7b2a8",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.rgh",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.192544,
// 		"longitude": -77.587294,
// 		"radius": 200.0,
// 		"friendly_name": "RGH",
// 		"icon": "mdi:hospital-building"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.343598+00:00",
// 	"last_updated": "2019-12-03T08:10:56.343598+00:00",
// 	"context": {
// 		"id": "a6c8adb59a2a448d96cf5249437b4286",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.urgent_care_now",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.212717,
// 		"longitude": -77.439028,
// 		"radius": 50.0,
// 		"friendly_name": "Urgent Care Now",
// 		"icon": "mdi:hospital"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.343954+00:00",
// 	"last_updated": "2019-12-03T08:10:56.343954+00:00",
// 	"context": {
// 		"id": "bfd8eadae4d64626a2867c07a4fbbbee",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.wwfd_station_1",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.203682,
// 		"longitude": -77.497451,
// 		"radius": 50.0,
// 		"friendly_name": "WWFD Station 1",
// 		"icon": "mdi:fire"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.344293+00:00",
// 	"last_updated": "2019-12-03T08:10:56.344293+00:00",
// 	"context": {
// 		"id": "b96c0fe80c5b4b8986deae020da4495a",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.wwfd_station_2",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.233833,
// 		"longitude": -77.511894,
// 		"radius": 50.0,
// 		"friendly_name": "WWFD Station 2",
// 		"icon": "mdi:fire"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.344627+00:00",
// 	"last_updated": "2019-12-03T08:10:56.344627+00:00",
// 	"context": {
// 		"id": "543728d0e44d475085d6da71967e6eea",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }, {
// 	"entity_id": "zone.wwfd_station_3",
// 	"state": "zoning",
// 	"attributes": {
// 		"hidden": true,
// 		"latitude": 43.182704,
// 		"longitude": -77.468922,
// 		"radius": 50.0,
// 		"friendly_name": "WWFD Station 3",
// 		"icon": "mdi:fire"
// 	},
// 	"last_changed": "2019-12-03T08:10:56.344976+00:00",
// 	"last_updated": "2019-12-03T08:10:56.344976+00:00",
// 	"context": {
// 		"id": "571caf78a0084d0f9f32eba762e6deea",
// 		"parent_id": null,
// 		"user_id": null
// 	}
// }]

    @POST
    suspend fun getZones(
        @Url url: HttpUrl,
        @Body request: IntegrationRequest
    ): Array<EntityResponse>
}
