package snippetsearcher.printscript.common

import org.printscript.common.Result
import org.printscript.runner.config.ConfigReadError
import tools.jackson.databind.json.JsonMapper

// Unas reglas inválidas son un error esperado del pedido, como una versión inexistente: se devuelve, no se lanza
sealed interface RulesResolution<out T> {
    data class Supported<T>(
        val config: T,
    ) : RulesResolution<T>

    data class Invalid(
        val message: String,
    ) : RulesResolution<Nothing>
}

// Pasa por texto para validarlas con el mismo loader que los archivos de configuración de la librería,
// que es el que conoce las claves, los tipos y los rangos
fun <T> resolveRules(
    rules: Map<String, Any?>,
    jsonMapper: JsonMapper,
    load: (String) -> Result<T, ConfigReadError>,
): RulesResolution<T> =
    when (val loaded = load(jsonMapper.writeValueAsString(rules))) {
        is Result.Success -> RulesResolution.Supported(loaded.value)
        is Result.Failure -> RulesResolution.Invalid(loaded.error.message)
    }
