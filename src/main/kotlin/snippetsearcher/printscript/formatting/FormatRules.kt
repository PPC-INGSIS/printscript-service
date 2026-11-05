package snippetsearcher.printscript.formatting

import org.printscript.common.Version
import org.printscript.formatter.config.BlankLines
import org.printscript.formatter.config.BracePosition
import org.printscript.formatter.config.FormatterConfig
import org.printscript.formatter.config.Indent
import org.printscript.formatter.config.Spacing

sealed interface FormatRuleType {
    data class Choice(
        val options: List<String>,
    ) : FormatRuleType

    data class Integer(
        val range: IntRange,
    ) : FormatRuleType

    // Solo true activa la regla: false equivale a no mandarla
    data object Flag : FormatRuleType
}

private fun spacing(value: Any) = if (value == "always") Spacing.SINGLE else Spacing.NONE

// El valor que recibe apply ya está validado contra type
enum class FormatRule(
    val id: String,
    val since: Version,
    val type: FormatRuleType,
    val apply: (FormatterConfig, Any) -> FormatterConfig,
) {
    SPACE_BEFORE_COLON(
        "space-before-colon",
        Version.V10,
        FormatRuleType.Choice(listOf("always", "never")),
        { c, v -> c.copy(spaceBeforeColon = spacing(v)) },
    ),
    SPACE_AFTER_COLON(
        "space-after-colon",
        Version.V10,
        FormatRuleType.Choice(listOf("always", "never")),
        { c, v -> c.copy(spaceAfterColon = spacing(v)) },
    ),
    SPACE_AROUND_ASSIGNMENT(
        "space-around-assignment",
        Version.V10,
        FormatRuleType.Choice(listOf("always", "never")),
        { c, v -> c.copy(spaceAroundAssignment = spacing(v)) },
    ),
    BLANK_LINES_AFTER_PRINTLN(
        "blank-lines-after-println",
        Version.V10,
        FormatRuleType.Integer(BlankLines.ALLOWED),
        { c, v -> c.copy(lineBreaksAfterPrintln = BlankLines.of(v as Int)) },
    ),
    LINE_BREAK_AFTER_STATEMENT(
        "line-break-after-statement",
        Version.V10,
        FormatRuleType.Flag,
        { c, _ -> c.copy(lineBreakAfterStatement = true) },
    ),
    SPACE_AROUND_OPERATORS(
        "space-around-operators",
        Version.V10,
        FormatRuleType.Flag,
        { c, _ -> c.copy(spaceSurroundingOperations = true) },
    ),
    SINGLE_SPACE_SEPARATION(
        "single-space-separation",
        Version.V10,
        FormatRuleType.Flag,
        { c, _ -> c.copy(singleSpaceSeparation = true) },
    ),
    IF_BRACE_POSITION(
        "if-brace-position",
        Version.V11,
        FormatRuleType.Choice(listOf("same-line", "next-line")),
        { c, v -> c.copy(ifBrace = if (v == "same-line") BracePosition.SAME_LINE else BracePosition.BELOW_LINE) },
    ),
    IF_INDENT(
        "if-indent",
        Version.V11,
        FormatRuleType.Integer(Indent.ALLOWED),
        { c, v -> c.copy(indentInsideIf = Indent.of(v as Int)) },
    ),
}

// Las claves del archivo de configuración de la librería, que /format aceptaba antes de tener reglas propias
sealed interface DeprecatedFormatKey {
    val key: String
    val rule: FormatRule

    // whenFalse null: con false la regla queda ausente
    data class Toggle(
        override val key: String,
        override val rule: FormatRule,
        val whenTrue: Any,
        val whenFalse: Any?,
    ) : DeprecatedFormatKey

    data class Renamed(
        override val key: String,
        override val rule: FormatRule,
    ) : DeprecatedFormatKey
}

val DEPRECATED_FORMAT_KEYS: List<DeprecatedFormatKey> =
    listOf(
        DeprecatedFormatKey.Toggle(
            "enforce-spacing-before-colon-in-declaration",
            FormatRule.SPACE_BEFORE_COLON,
            "always",
            "never",
        ),
        DeprecatedFormatKey.Toggle(
            "enforce-spacing-after-colon-in-declaration",
            FormatRule.SPACE_AFTER_COLON,
            "always",
            "never",
        ),
        DeprecatedFormatKey.Toggle(
            "enforce-spacing-around-equals",
            FormatRule.SPACE_AROUND_ASSIGNMENT,
            "always",
            "never",
        ),
        DeprecatedFormatKey.Toggle(
            "enforce-no-spacing-around-equals",
            FormatRule.SPACE_AROUND_ASSIGNMENT,
            "never",
            "always",
        ),
        DeprecatedFormatKey.Toggle(
            "mandatory-line-break-after-statement",
            FormatRule.LINE_BREAK_AFTER_STATEMENT,
            true,
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "mandatory-space-surrounding-operations",
            FormatRule.SPACE_AROUND_OPERATORS,
            true,
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "mandatory-single-space-separation",
            FormatRule.SINGLE_SPACE_SEPARATION,
            true,
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "if-brace-same-line",
            FormatRule.IF_BRACE_POSITION,
            "same-line",
            null,
        ),
        DeprecatedFormatKey.Toggle(
            "if-brace-below-line",
            FormatRule.IF_BRACE_POSITION,
            "next-line",
            null,
        ),
        DeprecatedFormatKey.Renamed("line-breaks-after-println", FormatRule.BLANK_LINES_AFTER_PRINTLN),
        DeprecatedFormatKey.Renamed("indent-inside-if", FormatRule.IF_INDENT),
    )
