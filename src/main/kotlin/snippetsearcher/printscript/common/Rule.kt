package snippetsearcher.printscript.common

import org.printscript.common.Version

// El valor ya parseado y cómo aplicarlo: el parseo y el apply comparten el tipo de la regla, sin casts
class Setting<C>(
    val value: Any,
    val applyTo: (C) -> C,
)

class Rule<C, T : Any>(
    val id: String,
    val since: Version,
    val type: RuleType<T>,
    val description: String,
    // El valor que la UI propone al activar la regla, tal como lo manda el cliente
    val default: Any? = null,
    private val apply: (C, T) -> C,
) {
    fun parse(value: Any?): Parsed<Setting<C>> =
        when (val parsed = type.parse(value)) {
            is Parsed.Valid -> Parsed.Valid(Setting(parsed.value) { apply(it, parsed.value) })
            is Parsed.Problem -> parsed
        }
}

// Las claves del archivo de configuración de la librería, que el servicio aceptaba antes de tener reglas propias
sealed interface DeprecatedKey<C> {
    val key: String
    val rule: Rule<C, *>

    // Un valor null en la tabla: con ese valor la regla queda ausente
    data class Mapped<C>(
        override val key: String,
        override val rule: Rule<C, *>,
        val values: Map<Any, Any?>,
    ) : DeprecatedKey<C>

    data class Renamed<C>(
        override val key: String,
        override val rule: Rule<C, *>,
    ) : DeprecatedKey<C>
}

// Todo lo que un endpoint necesita para resolver sus reglas. kind nombra el tipo de regla en el mensaje de una
// desconocida: "formato", "lint"
class RuleSet<C>(
    val rules: List<Rule<C, *>>,
    val deprecated: List<DeprecatedKey<C>>,
    val empty: C,
    val kind: String,
)
