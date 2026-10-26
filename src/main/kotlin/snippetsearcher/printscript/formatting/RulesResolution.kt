package snippetsearcher.printscript.formatting

import org.printscript.common.Result
import org.printscript.formatter.config.FormatterConfig
import org.printscript.runner.config.loadFormatterConfig
import tools.jackson.databind.json.JsonMapper

// Unas reglas inválidas son un error esperado del pedido, como una versión inexistente: se devuelve, no se lanza
sealed interface RulesResolution {
    data class Supported(
        val config: FormatterConfig,
    ) : RulesResolution

    data class Invalid(
        val message: String,
    ) : RulesResolution
}

// Pasa por texto para validarlas con el mismo loader que los archivos de configuración de la librería,
// que es el que conoce las claves, los tipos y los rangos
fun resolveRules(
    rules: Map<String, Any?>,
    jsonMapper: JsonMapper,
): RulesResolution =
    when (val loaded = loadFormatterConfig(jsonMapper.writeValueAsString(rules))) {
        is Result.Success -> RulesResolution.Supported(loaded.value)
        is Result.Failure -> RulesResolution.Invalid(loaded.error.message)
    }
