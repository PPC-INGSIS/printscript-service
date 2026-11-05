package snippetsearcher.printscript.formatting

import org.printscript.common.Version
import org.printscript.formatter.config.BlankLines
import org.printscript.formatter.config.BracePosition
import org.printscript.formatter.config.FormatterConfig
import org.printscript.formatter.config.Indent
import org.printscript.formatter.config.Spacing
import java.math.BigInteger

sealed interface Parsed<out T> {
    data class Valid<T>(
        val value: T,
    ) : Parsed<T>

    data class Problem(
        val message: String,
    ) : Parsed<Nothing>
}

sealed interface FormatRuleType<T : Any> {
    fun parse(value: Any?): Parsed<T>

    data class Choice<T : Any>(
        val options: Map<String, T>,
    ) : FormatRuleType<T> {
        override fun parse(value: Any?): Parsed<T> =
            options[value]?.let { Parsed.Valid(it) }
                ?: Parsed.Problem("tiene que ser ${options.keys.joinToString(" o ") { "'$it'" }}")
    }

    // of es el constructor de la librería, que devuelve null fuera de rango
    class Integer<T : Any>(
        val range: IntRange,
        private val of: (Int) -> T?,
    ) : FormatRuleType<T> {
        // Jackson deja en Long o BigInteger los enteros que no entran en un Int: son enteros, pero fuera de rango
        override fun parse(value: Any?): Parsed<T> =
            when (value) {
                is Int -> of(value)?.let { Parsed.Valid(it) } ?: outOfRange(value)
                is Long, is BigInteger -> outOfRange(value)
                else -> Parsed.Problem("espera un número entero")
            }

        private fun outOfRange(value: Any) =
            Parsed.Problem("acepta valores entre ${range.first} y ${range.last}, se recibió $value")
    }

    // Solo true activa la regla: false equivale a no mandarla
    data object Flag : FormatRuleType<Boolean> {
        override fun parse(value: Any?): Parsed<Boolean> =
            if (value is Boolean) Parsed.Valid(value) else Parsed.Problem("solo acepta true")
    }
}

// El valor ya parseado y cómo aplicarlo: el parseo y el apply comparten el tipo de la regla, sin casts
class FormatSetting(
    val value: Any,
    val applyTo: (FormatterConfig) -> FormatterConfig,
)

class FormatRule<T : Any>(
    val id: String,
    val since: Version,
    val type: FormatRuleType<T>,
    private val apply: (FormatterConfig, T) -> FormatterConfig,
) {
    fun parse(value: Any?): Parsed<FormatSetting> =
        when (val parsed = type.parse(value)) {
            is Parsed.Valid -> Parsed.Valid(FormatSetting(parsed.value) { apply(it, parsed.value) })
            is Parsed.Problem -> parsed
        }
}

private val SPACING = mapOf("always" to Spacing.SINGLE, "never" to Spacing.NONE)

private val SPACE_BEFORE_COLON =
    FormatRule(
        "space-before-colon",
        Version.V10,
        FormatRuleType.Choice(SPACING),
    ) { c, v -> c.copy(spaceBeforeColon = v) }

private val SPACE_AFTER_COLON =
    FormatRule(
        "space-after-colon",
        Version.V10,
        FormatRuleType.Choice(SPACING),
    ) { c, v -> c.copy(spaceAfterColon = v) }

private val SPACE_AROUND_ASSIGNMENT =
    FormatRule(
        "space-around-assignment",
        Version.V10,
        FormatRuleType.Choice(SPACING),
    ) { c, v -> c.copy(spaceAroundAssignment = v) }

private val BLANK_LINES_AFTER_PRINTLN =
    FormatRule(
        "blank-lines-after-println",
        Version.V10,
        FormatRuleType.Integer(BlankLines.ALLOWED) { BlankLines.of(it) },
    ) { c, v -> c.copy(lineBreaksAfterPrintln = v) }

private val LINE_BREAK_AFTER_STATEMENT =
    FormatRule(
        "line-break-after-statement",
        Version.V10,
        FormatRuleType.Flag,
    ) { c, v -> c.copy(lineBreakAfterStatement = v) }

private val SPACE_AROUND_OPERATORS =
    FormatRule(
        "space-around-operators",
        Version.V10,
        FormatRuleType.Flag,
    ) { c, v -> c.copy(spaceSurroundingOperations = v) }

private val SINGLE_SPACE_SEPARATION =
    FormatRule(
        "single-space-separation",
        Version.V10,
        FormatRuleType.Flag,
    ) { c, v -> c.copy(singleSpaceSeparation = v) }

private val IF_BRACE_POSITION =
    FormatRule(
        "if-brace-position",
        Version.V11,
        FormatRuleType.Choice(mapOf("same-line" to BracePosition.SAME_LINE, "next-line" to BracePosition.BELOW_LINE)),
    ) { c, v -> c.copy(ifBrace = v) }

private val IF_INDENT =
    FormatRule(
        "if-indent",
        Version.V11,
        FormatRuleType.Integer(Indent.ALLOWED) { Indent.of(it) },
    ) { c, v -> c.copy(indentInsideIf = v) }

val FORMAT_RULES: List<FormatRule<*>> =
    listOf(
        SPACE_BEFORE_COLON,
        SPACE_AFTER_COLON,
        SPACE_AROUND_ASSIGNMENT,
        BLANK_LINES_AFTER_PRINTLN,
        LINE_BREAK_AFTER_STATEMENT,
        SPACE_AROUND_OPERATORS,
        SINGLE_SPACE_SEPARATION,
        IF_BRACE_POSITION,
        IF_INDENT,
    )

// Las claves del archivo de configuración de la librería, que /format aceptaba antes de tener reglas propias
sealed interface DeprecatedFormatKey {
    val key: String
    val rule: FormatRule<*>

    // whenFalse null: con false la regla queda ausente
    data class Toggle(
        override val key: String,
        override val rule: FormatRule<*>,
        val whenTrue: Any,
        val whenFalse: Any?,
    ) : DeprecatedFormatKey

    data class Renamed(
        override val key: String,
        override val rule: FormatRule<*>,
    ) : DeprecatedFormatKey
}

val DEPRECATED_FORMAT_KEYS: List<DeprecatedFormatKey> =
    listOf(
        DeprecatedFormatKey.Toggle(
            "enforce-spacing-before-colon-in-declaration",
            SPACE_BEFORE_COLON,
            "always",
            "never",
        ),
        DeprecatedFormatKey.Toggle(
            "enforce-spacing-after-colon-in-declaration",
            SPACE_AFTER_COLON,
            "always",
            "never",
        ),
        DeprecatedFormatKey.Toggle(
            "enforce-spacing-around-equals",
            SPACE_AROUND_ASSIGNMENT,
            "always",
            "never",
        ),
        DeprecatedFormatKey.Toggle(
            "enforce-no-spacing-around-equals",
            SPACE_AROUND_ASSIGNMENT,
            "never",
            "always",
        ),
        DeprecatedFormatKey.Toggle(
            "mandatory-line-break-after-statement",
            LINE_BREAK_AFTER_STATEMENT,
            true,
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "mandatory-space-surrounding-operations",
            SPACE_AROUND_OPERATORS,
            true,
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "mandatory-single-space-separation",
            SINGLE_SPACE_SEPARATION,
            true,
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "if-brace-same-line",
            IF_BRACE_POSITION,
            "same-line",
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "if-brace-below-line",
            IF_BRACE_POSITION,
            "next-line",
            null,
        ),
        DeprecatedFormatKey.Renamed("line-breaks-after-println", BLANK_LINES_AFTER_PRINTLN),
        DeprecatedFormatKey.Renamed("indent-inside-if", IF_INDENT),
    )
