package snippetsearcher.printscript.common

import org.printscript.common.Version

// Unas reglas inválidas son un error esperado del pedido, como una versión inexistente: se devuelve, no se lanza
sealed interface RulesResolution<out C> {
    data class Supported<C>(
        val config: C,
        val ignoredRules: List<String>,
    ) : RulesResolution<C>

    data class Invalid(
        val message: String,
        val rule: String,
    ) : RulesResolution<Nothing>
}

private data class Entry<C>(
    val rule: Rule<C, *>,
    val setting: Setting<C>,
)

fun <C> resolveRules(
    rules: Map<String, Any?>,
    version: Version,
    ruleSet: RuleSet<C>,
): RulesResolution<C> {
    val entries = mutableListOf<Entry<C>>()
    for ((key, value) in rules) {
        when (val parsed = entryFor(key, value, ruleSet)) {
            is Parsed.Problem -> return RulesResolution.Invalid(parsed.message, key)
            is Parsed.Valid -> parsed.value?.let { entries += it }
        }
    }
    val (applied, ignored) = entries.partition { it.rule.since <= version }
    return RulesResolution.Supported(
        config = applied.fold(ruleSet.empty) { config, entry -> entry.setting.applyTo(config) },
        ignoredRules = ignored.map { it.rule.id }.distinct(),
    )
}

// Null: la regla queda ausente (un flag en false)
private fun <C> entryFor(
    key: String,
    value: Any?,
    ruleSet: RuleSet<C>,
): Parsed<Entry<C>?> {
    val rule =
        ruleSet.rules.find { it.id == key }
            ?: return Parsed.Problem("Regla de ${ruleSet.kind} desconocida: '$key'")
    return when (val parsed = rule.parse(value)) {
        is Parsed.Problem -> Parsed.Problem("La regla '$key' ${parsed.message}")
        is Parsed.Valid -> Parsed.Valid(Entry(rule, parsed.value).takeIf { parsed.value.value != false })
    }
}
