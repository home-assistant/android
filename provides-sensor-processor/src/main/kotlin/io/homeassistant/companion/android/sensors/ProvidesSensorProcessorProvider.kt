package io.homeassistant.companion.android.sensors

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

private const val MODULE_SUFFIX_ARG_NAME = "providesSensorModuleSuffix"

/**
 * Entry point registered through `META-INF/services` so KSP can discover and instantiate the
 * [ProvidesSensorProcessor].
 *
 * The generated Hilt module name is suffixed with the `providesSensorModuleSuffix` processor option
 * so that each compiled module produces a uniquely named module and the
 * generated names do not collide on the merged classpath.
 */
class ProvidesSensorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = ProvidesSensorProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
        moduleSuffix = environment.options[MODULE_SUFFIX_ARG_NAME]?.capitalize()
            ?: throw IllegalArgumentException("ksp arg $MODULE_SUFFIX_ARG_NAME is missing"),
    )
}
