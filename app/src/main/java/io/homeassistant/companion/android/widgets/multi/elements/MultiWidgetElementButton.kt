package io.homeassistant.companion.android.widgets.multi.elements

import android.view.View
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.homeassistant.companion.android.common.data.integration.Service
import io.homeassistant.companion.android.widgets.common.ServiceFieldBinder
import kotlinx.android.synthetic.main.widget_button_configure.view.*
import kotlinx.android.synthetic.main.widget_multi_config_button.view.*

class MultiWidgetElementButton(
    private val services: HashMap<String, Service>
) : MultiWidgetElement {
    // Define the type of the element
    override val type: MultiWidgetElementType
        get() = MultiWidgetElementType.TYPE_BUTTON

    // Create a random tag for identifying the icon dialog
    val tag: String = java.util.UUID.randomUUID().toString()

    // Create variables to store service call information
    lateinit var domain: String
    lateinit var service: String
    lateinit var serviceData: String

    // Create variable to store icon ID
    var iconId: Int = 62017

    //  Create variable to store layout views
    lateinit var layout: View
    lateinit var dynamicFields: ArrayList<ServiceFieldBinder>

    override fun retrieveFinalValues() {
        // Analyze service call information
        val serviceText = layout.widget_element_service_text.text.toString()
        val serviceDataMap = HashMap<String, Any>()
        dynamicFields.forEach {
            if (it.value != null) {
                serviceDataMap[it.field] = it.value!!
            }
        }

        domain = services[serviceText]?.domain ?: serviceText.split(".", limit = 2)[0]
        service = services[serviceText]?.service ?: serviceText.split(".", limit = 2)[1]
        serviceData = jacksonObjectMapper().writeValueAsString(serviceDataMap)

        // Fetch icon ID from selector
        iconId = layout.widget_config_icon_selector.tag as Int? ?: iconId
    }
}
