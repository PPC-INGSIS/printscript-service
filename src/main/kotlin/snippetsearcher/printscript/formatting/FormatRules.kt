package snippetsearcher.printscript.formatting

import org.printscript.common.Version
import org.printscript.formatter.config.BlankLines
import org.printscript.formatter.config.BracePosition
import org.printscript.formatter.config.FormatterConfig
import org.printscript.formatter.config.Indent
import org.printscript.formatter.config.Spacing
import snippetsearcher.printscript.common.DeprecatedKey
import snippetsearcher.printscript.common.Rule
import snippetsearcher.printscript.common.RuleSet
import snippetsearcher.printscript.common.RuleType

private val SPACING = mapOf("always" to Spacing.SINGLE, "never" to Spacing.NONE)

private val SPACE_BEFORE_COLON =
    Rule(
        "space-before-colon",
        Version.V10,
        RuleType.Choice(SPACING),
    ) { c: FormatterConfig, v -> c.copy(spaceBeforeColon = v) }

private val SPACE_AFTER_COLON =
    Rule(
        "space-after-colon",
        Version.V10,
        RuleType.Choice(SPACING),
    ) { c: FormatterConfig, v -> c.copy(spaceAfterColon = v) }

private val SPACE_AROUND_ASSIGNMENT =
    Rule(
        "space-around-assignment",
        Version.V10,
        RuleType.Choice(SPACING),
    ) { c: FormatterConfig, v -> c.copy(spaceAroundAssignment = v) }

private val BLANK_LINES_AFTER_PRINTLN =
    Rule(
        "blank-lines-after-println",
        Version.V10,
        RuleType.Integer(BlankLines.ALLOWED) { BlankLines.of(it) },
    ) { c: FormatterConfig, v -> c.copy(lineBreaksAfterPrintln = v) }

private val LINE_BREAK_AFTER_STATEMENT =
    Rule(
        "line-break-after-statement",
        Version.V10,
        RuleType.Flag,
    ) { c: FormatterConfig, v -> c.copy(lineBreakAfterStatement = v) }

private val SPACE_AROUND_OPERATORS =
    Rule(
        "space-around-operators",
        Version.V10,
        RuleType.Flag,
    ) { c: FormatterConfig, v -> c.copy(spaceSurroundingOperations = v) }

private val SINGLE_SPACE_SEPARATION =
    Rule(
        "single-space-separation",
        Version.V10,
        RuleType.Flag,
    ) { c: FormatterConfig, v -> c.copy(singleSpaceSeparation = v) }

private val IF_BRACE_POSITION =
    Rule(
        "if-brace-position",
        Version.V11,
        RuleType.Choice(mapOf("same-line" to BracePosition.SAME_LINE, "next-line" to BracePosition.BELOW_LINE)),
    ) { c: FormatterConfig, v -> c.copy(ifBrace = v) }

private val IF_INDENT =
    Rule(
        "if-indent",
        Version.V11,
        RuleType.Integer(Indent.ALLOWED) { Indent.of(it) },
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

val DEPRECATED_FORMAT_KEYS: List<DeprecatedKey<FormatterConfig>> =
    listOf(
        DeprecatedKey.Mapped(
            "enforce-spacing-before-colon-in-declaration",
            SPACE_BEFORE_COLON,
            mapOf(true to "always", false to "never"),
        ),
        DeprecatedKey.Mapped(
            "enforce-spacing-after-colon-in-declaration",
            SPACE_AFTER_COLON,
            mapOf(true to "always", false to "never"),
        ),
        DeprecatedKey.Mapped(
            "enforce-spacing-around-equals",
            SPACE_AROUND_ASSIGNMENT,
            mapOf(true to "always", false to "never"),
        ),
        DeprecatedKey.Mapped(
            "enforce-no-spacing-around-equals",
            SPACE_AROUND_ASSIGNMENT,
            mapOf(true to "never", false to "always"),
        ),
        DeprecatedKey.Mapped(
            "mandatory-line-break-after-statement",
            LINE_BREAK_AFTER_STATEMENT,
            mapOf(true to true, false to null),
        ),
        DeprecatedKey.Mapped(
            "mandatory-space-surrounding-operations",
            SPACE_AROUND_OPERATORS,
            mapOf(true to true, false to null),
        ),
        DeprecatedKey.Mapped(
            "mandatory-single-space-separation",
            SINGLE_SPACE_SEPARATION,
            mapOf(true to true, false to null),
        ),
        DeprecatedKey.Mapped(
            "if-brace-same-line",
            IF_BRACE_POSITION,
            mapOf(true to "same-line", false to null),
        ),
        DeprecatedKey.Mapped(
            "if-brace-below-line",
            IF_BRACE_POSITION,
            mapOf(true to "next-line", false to null),
        ),
        DeprecatedKey.Renamed("line-breaks-after-println", BLANK_LINES_AFTER_PRINTLN),
        DeprecatedKey.Renamed("indent-inside-if", IF_INDENT),
    )

val FORMAT_RULE_SET = RuleSet(FORMAT_RULES, DEPRECATED_FORMAT_KEYS, FormatterConfig(), "formato")
