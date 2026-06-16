package io.homeassistant.companion.android.sensorcatalog

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

private const val MODULE_SUFFIX_ARG_NAME = "catalogModuleSuffix"

/**
 * Entry point registered through `META-INF/services` so KSP can discover and instantiate the
 * [SensorCatalogProcessor].
 *
 * The generated Hilt module name is suffixed with the `catalogModuleSuffix` processor option
 * so that each compiled module produces a uniquely named module and the
 * generated names do not collide on the merged classpath.
 */
class SensorCatalogProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = SensorCatalogProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
        moduleSuffix = environment.options[MODULE_SUFFIX_ARG_NAME]?.capitalize()
            ?: throw IllegalArgumentException("ksp arg $MODULE_SUFFIX_ARG_NAME is missing"),
    )
}
