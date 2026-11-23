package snippetsearcher.printscript.formatting

import org.printscript.common.Version
import org.printscript.formatter.config.BlankLines
import org.printscript.formatter.config.BracePosition
import org.printscript.formatter.config.FormatterConfig
import org.printscript.formatter.config.Indent
import org.printscript.formatter.config.Spacing
import snippetsearcher.printscript.common.Rule
import snippetsearcher.printscript.common.RuleSet
import snippetsearcher.printscript.common.RuleType

private val SPACING = mapOf("always" to Spacing.SINGLE, "never" to Spacing.NONE)

private val SPACE_BEFORE_COLON =
    Rule(
        "space-before-colon",
        Version.V10,
        RuleType.Choice(SPACING),
        description = "Espacio antes del ':' en una declaración",
        default = "never",
    ) { c: FormatterConfig, v -> c.copy(spaceBeforeColon = v) }

private val SPACE_AFTER_COLON =
    Rule(
        "space-after-colon",
        Version.V10,
        RuleType.Choice(SPACING),
        description = "Espacio después del ':' en una declaración",
        default = "always",
    ) { c: FormatterConfig, v -> c.copy(spaceAfterColon = v) }

private val SPACE_AROUND_ASSIGNMENT =
    Rule(
        "space-around-assignment",
        Version.V10,
        RuleType.Choice(SPACING),
        description = "Espacios alrededor del '='",
        default = "always",
    ) { c: FormatterConfig, v -> c.copy(spaceAroundAssignment = v) }

private val BLANK_LINES_AFTER_PRINTLN =
    Rule(
        "blank-lines-after-println",
        Version.V10,
        RuleType.Integer(BlankLines.ALLOWED) { BlankLines.of(it) },
        description = "Líneas en blanco después de cada println",
        default = 0,
    ) { c: FormatterConfig, v -> c.copy(lineBreaksAfterPrintln = v) }

private val LINE_BREAK_AFTER_STATEMENT =
    Rule(
        "line-break-after-statement",
        Version.V10,
        RuleType.Flag,
        description = "Salto de línea después de cada sentencia",
    ) { c: FormatterConfig, v -> c.copy(lineBreakAfterStatement = v) }

private val SPACE_AROUND_OPERATORS =
    Rule(
        "space-around-operators",
        Version.V10,
        RuleType.Flag,
        description = "Espacios alrededor de los operadores",
    ) { c: FormatterConfig, v -> c.copy(spaceSurroundingOperations = v) }

private val SINGLE_SPACE_SEPARATION =
    Rule(
        "single-space-separation",
        Version.V10,
        RuleType.Flag,
        description = "Un solo espacio entre elementos",
    ) { c: FormatterConfig, v -> c.copy(singleSpaceSeparation = v) }

private val IF_BRACE_POSITION =
    Rule(
        "if-brace-position",
        Version.V11,
        RuleType.Choice(mapOf("same-line" to BracePosition.SAME_LINE, "next-line" to BracePosition.BELOW_LINE)),
        description = "Dónde va la llave que abre un if",
        default = "same-line",
    ) { c: FormatterConfig, v -> c.copy(ifBrace = v) }

private val IF_INDENT =
    Rule(
        "if-indent",
        Version.V11,
        RuleType.Integer(Indent.ALLOWED) { Indent.of(it) },
        description = "Espacios de indentación dentro de un if",
        default = 4,
    ) { c: FormatterConfig, v -> c.copy(indentInsideIf = v) }

val FORMAT_RULES: List<Rule<FormatterConfig, *>> =
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

val FORMAT_RULE_SET = RuleSet(FORMAT_RULES, FormatterConfig(), "formato")
