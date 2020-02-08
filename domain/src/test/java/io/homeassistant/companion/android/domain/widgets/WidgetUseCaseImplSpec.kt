package io.homeassistant.companion.android.domain.widgets

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object WidgetUseCaseImplSpec : Spek({
    describe("widget use case") {

        val widgetRepository by memoized {
            mockk<WidgetRepository>(
                relaxed = true,
                relaxUnitFun = true
            )
        }
        val useCase by memoized { WidgetUseCaseImpl(widgetRepository) }

        describe("save service call data") {
            beforeEachTest {
                runBlocking {
                    useCase.saveServiceCallData(
                        1,
                        "domain",
                        "service",
                        "{\"brightness\":\"255\",\"entity_id\":\"light.dummy_light\",\"color_temp\":\"2700\"}"
                    )
                }
            }

            it("should call the repository") {
                coVerify {
                    widgetRepository.saveServiceCallData(
                        1,
                        "domain",
                        "service",
                        "{\"brightness\":\"255\",\"entity_id\":\"light.dummy_light\",\"color_temp\":\"2700\"}"
                    )
                }
            }
        }

        describe("load domain") {
            beforeEachTest {
                runBlocking { useCase.loadDomain(1) }
            }

            it("should call the repository") {
                coVerify { widgetRepository.loadDomain(1) }
            }
        }

        describe("load service") {
            beforeEachTest {
                runBlocking { useCase.loadService(1) }
            }

            it("should call the repository") {
                coVerify { widgetRepository.loadService(1) }
            }
        }

        describe("load service data") {
            beforeEachTest {
                runBlocking { useCase.loadServiceData(1) }
            }

            it("should call the repository") {
                coVerify { widgetRepository.loadServiceData(1) }
            }
        }

        describe("load icon") {
            beforeEachTest {
                runBlocking { useCase.loadIcon(1) }
            }

            it("should call the repository") {
                coVerify { widgetRepository.loadIcon(1) }
            }
        }

        describe("load label") {
            beforeEachTest {
                runBlocking { useCase.loadLabel(1) }
            }

            it("should call the repository") {
                coVerify { widgetRepository.loadLabel(1) }
            }
        }

        describe("save icon") {
            beforeEachTest {
                runBlocking { useCase.saveIcon(1, "icon_res_name") }
            }

            it("should call the repository") {
                coVerify { widgetRepository.saveIcon(1, "icon_res_name") }
            }
        }

        describe("save label") {
            beforeEachTest {
                runBlocking { useCase.saveLabel(1, "label") }
            }

            it("should call the repository") {
                coVerify { widgetRepository.saveLabel(1, "label") }
            }
        }

        describe("delete widget data") {
            beforeEachTest {
                runBlocking { useCase.deleteWidgetData(1) }
            }

            it("should call the repository") {
                coVerify { widgetRepository.deleteWidgetData(1) }
            }
        }
    }
})
