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
    val rule: FormatRule,
    val value: Any,
)

fun resolveFormatRules(
    rules: Map<String, Any?>,
    version: Version,
): FormatRulesResolution {
    rules.firstNotNullOfOrNull { (key, value) -> problemWith(key, value) }?.let { return it }
    val entries = rules.mapNotNull { (key, value) -> translate(key, value) }
    findConflict(entries)?.let { return it }
    val (applied, ignored) = entries.partition { it.rule.since <= version }
    return FormatRulesResolution.Supported(
        config = applied.fold(FormatterConfig()) { config, entry -> entry.rule.apply(config, entry.value) },
        ignoredRules = ignored.map { it.rule.id }.distinct(),
        deprecatedKeys = rules.keys.filter { deprecatedKey(it) != null },
    )
}

private fun deprecatedKey(key: String) = DEPRECATED_FORMAT_KEYS.find { it.key == key }

private fun ruleFor(key: String) = FormatRule.entries.find { it.id == key } ?: deprecatedKey(key)?.rule

private fun problemWith(
    key: String,
    value: Any?,
): FormatRulesResolution.Invalid? {
    val rule = ruleFor(key) ?: return FormatRulesResolution.Invalid("Regla de formato desconocida: '$key'", key)
    val problem =
        if (deprecatedKey(key) is DeprecatedFormatKey.Toggle) {
            "tiene que ser true o false".takeIf { value !is Boolean }
        } else {
            problemWithValue(rule.type, value)
        }
    return problem?.let { FormatRulesResolution.Invalid("La regla '$key' $it", key) }
}

private fun problemWithValue(
    type: FormatRuleType,
    value: Any?,
): String? =
    when (type) {
        is FormatRuleType.Choice ->
            "tiene que ser ${type.options.joinToString(" o ") { "'$it'" }}".takeIf { value !in type.options }
        is FormatRuleType.Integer ->
            when (value) {
                !is Int -> "espera un número entero"
                !in type.range -> "acepta valores entre ${type.range.first} y ${type.range.last}, se recibió $value"
                else -> null
            }
        FormatRuleType.Flag -> "solo acepta true".takeIf { value !is Boolean }
    }

// Los alias se traducen antes de comparar y aplicar. Null: la regla queda ausente (un flag en false también)
private fun translate(
    key: String,
    value: Any?,
): Entry? {
    val rule = ruleFor(key) ?: return null
    val deprecated = deprecatedKey(key)
    val translated =
        when {
            deprecated !is DeprecatedFormatKey.Toggle -> value
            value == true -> deprecated.whenTrue
            else -> deprecated.whenFalse
        }
    return if (translated == null || translated == false) null else Entry(key, rule, translated)
}

private fun findConflict(entries: List<Entry>): FormatRulesResolution.Invalid? =
    entries.groupBy { it.rule }.firstNotNullOfOrNull { (rule, sameRule) ->
        val first = sameRule.first()
        sameRule.find { it.value != first.value }?.let {
            val message = "'${first.key}' y '${it.key}' definen '${rule.id}' con valores distintos"
            FormatRulesResolution.Invalid(message, rule.id)
        }
    }
