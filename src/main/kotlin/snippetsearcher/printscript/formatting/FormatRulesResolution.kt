package snippetsearcher.printscript.formatting

import org.printscript.common.Version
import org.printscript.formatter.config.FormatterConfig

// Unas reglas inválidas son un error esperado del pedido, como una versión inexistente: se devuelve, no se lanza
sealed interface FormatRulesResolution {
    data class Supported(
        val config: FormatterConfig,
        val ignoredRules: List<String>,
        val deprecatedKeys: List<String>,
    ) : FormatRulesResolution

    data class Invalid(
        val message: String,
        val rule: String,
    ) : FormatRulesResolution
}

// key es la clave tal como llegó en el pedido, para nombrarla en los conflictos
private data class Entry(
    val key: String,
    val rule: FormatRule<*>,
    val setting: FormatSetting,
)

fun resolveFormatRules(
    rules: Map<String, Any?>,
    version: Version,
): FormatRulesResolution {
    val entries = mutableListOf<Entry>()
    for ((key, value) in rules) {
        when (val parsed = entryFor(key, value)) {
            is Parsed.Problem -> return FormatRulesResolution.Invalid(parsed.message, key)
            is Parsed.Valid -> parsed.value?.let { entries += it }
        }
    }
    findConflict(entries)?.let { return it }
    val (applied, ignored) = entries.partition { it.rule.since <= version }
    return FormatRulesResolution.Supported(
        config = applied.fold(FormatterConfig()) { config, entry -> entry.setting.applyTo(config) },
        ignoredRules = ignored.map { it.rule.id }.distinct(),
        deprecatedKeys = rules.keys.filter { deprecatedKey(it) != null },
    )
}

private fun deprecatedKey(key: String) = DEPRECATED_FORMAT_KEYS.find { it.key == key }

// Los alias se traducen antes de parsear, así hay un solo camino de validación.
// Null: la regla queda ausente (un alias que con false no define nada, o un flag en false)
private fun entryFor(
    key: String,
    value: Any?,
): Parsed<Entry?> {
    val deprecated = deprecatedKey(key)
    val rule =
        FORMAT_RULES.find { it.id == key } ?: deprecated?.rule
            ?: return Parsed.Problem("Regla de formato desconocida: '$key'")
    val parsed =
        when {
            deprecated !is DeprecatedFormatKey.Toggle -> rule.parse(value)
            value !is Boolean -> Parsed.Problem("tiene que ser true o false")
            else -> rule.parse(if (value) deprecated.whenTrue else deprecated.whenFalse ?: return Parsed.Valid(null))
        }
    return when (parsed) {
        is Parsed.Problem -> Parsed.Problem("La regla '$key' ${parsed.message}")
        is Parsed.Valid -> Parsed.Valid(Entry(key, rule, parsed.value).takeIf { parsed.value.value != false })
    }
}

private fun findConflict(entries: List<Entry>): FormatRulesResolution.Invalid? =
    entries.groupBy { it.rule }.firstNotNullOfOrNull { (rule, sameRule) ->
        val first = sameRule.first()
        sameRule.find { it.setting.value != first.setting.value }?.let {
            val message = "'${first.key}' y '${it.key}' definen '${rule.id}' con valores distintos"
            FormatRulesResolution.Invalid(message, rule.id)
        }
    }
