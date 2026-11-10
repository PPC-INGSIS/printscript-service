package snippetsearcher.printscript.common

import org.printscript.common.Version

// Unas reglas inválidas son un error esperado del pedido, como una versión inexistente: se devuelve, no se lanza
sealed interface RulesResolution<out C> {
    data class Supported<C>(
        val config: C,
        val ignoredRules: List<String>,
        val deprecatedKeys: List<String>,
    ) : RulesResolution<C>

    data class Invalid(
        val message: String,
        val rule: String,
    ) : RulesResolution<Nothing>
}

// key es la clave tal como llegó en el pedido, para nombrarla en los conflictos
private data class Entry<C>(
    val key: String,
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
    findConflict(entries)?.let { return it }
    val (applied, ignored) = entries.partition { it.rule.since <= version }
    return RulesResolution.Supported(
        config = applied.fold(ruleSet.empty) { config, entry -> entry.setting.applyTo(config) },
        ignoredRules = ignored.map { it.rule.id }.distinct(),
        deprecatedKeys = rules.keys.filter { key -> ruleSet.deprecated.any { it.key == key } },
    )
}

// Los alias se traducen antes de parsear, así hay un solo camino de validación.
// Null: la regla queda ausente (un alias que con ese valor no define nada, o un flag en false)
private fun <C> entryFor(
    key: String,
    value: Any?,
    ruleSet: RuleSet<C>,
): Parsed<Entry<C>?> {
    val deprecated = ruleSet.deprecated.find { it.key == key }
    val rule =
        ruleSet.rules.find { it.id == key } ?: deprecated?.rule
            ?: return Parsed.Problem("Regla de ${ruleSet.kind} desconocida: '$key'")
    val parsed =
        when {
            deprecated !is DeprecatedKey.Mapped -> rule.parse(value)
            value == null || value !in deprecated.values -> Parsed.Problem("tiene que ser ${options(deprecated)}")
            else -> rule.parse(deprecated.values[value] ?: return Parsed.Valid(null))
        }
    return when (parsed) {
        is Parsed.Problem -> Parsed.Problem("La regla '$key' ${parsed.message}")
        is Parsed.Valid -> Parsed.Valid(Entry(key, rule, parsed.value).takeIf { parsed.value.value != false })
    }
}

private fun options(deprecated: DeprecatedKey.Mapped<*>) =
    deprecated.values.keys.joinToString(" o ") { if (it is String) "'$it'" else "$it" }

private fun <C> findConflict(entries: List<Entry<C>>): RulesResolution.Invalid? =
    entries.groupBy { it.rule }.firstNotNullOfOrNull { (rule, sameRule) ->
        val first = sameRule.first()
        sameRule.find { it.setting.value != first.setting.value }?.let {
            val message = "'${first.key}' y '${it.key}' definen '${rule.id}' con valores distintos"
            RulesResolution.Invalid(message, rule.id)
        }
    }
