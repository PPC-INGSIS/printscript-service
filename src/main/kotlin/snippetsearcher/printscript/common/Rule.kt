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

// Todo lo que un endpoint necesita para resolver sus reglas. kind nombra el tipo de regla en el mensaje de una
// desconocida: "formato", "lint"
class RuleSet<C>(
    val rules: List<Rule<C, *>>,
    val empty: C,
    val kind: String,
)
