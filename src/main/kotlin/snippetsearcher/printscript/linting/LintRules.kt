package snippetsearcher.printscript.linting

import org.printscript.analyzer.config.AnalyzerConfig
import org.printscript.analyzer.config.CamelCase
import org.printscript.analyzer.config.NamingConvention
import org.printscript.analyzer.config.SnakeCase
import org.printscript.common.Version
import snippetsearcher.printscript.common.DeprecatedKey
import snippetsearcher.printscript.common.Rule
import snippetsearcher.printscript.common.RuleSet
import snippetsearcher.printscript.common.RuleType

// Los ids son los mismos `rule` que la librería emite en las violaciones
private val IDENTIFIER_NAMING =
    Rule(
        "identifier-naming",
        Version.V10,
        RuleType.Choice<NamingConvention>(mapOf("camel-case" to CamelCase, "snake-case" to SnakeCase)),
    ) { c: AnalyzerConfig, v -> c.copy(namingConvention = v) }

private val PRINTLN_ARGUMENT =
    Rule(
        "println-argument",
        Version.V10,
        RuleType.Flag,
    ) { c: AnalyzerConfig, v -> c.copy(restrictPrintlnArguments = v) }

private val READ_INPUT_ARGUMENT =
    Rule(
        "read-input-argument",
        Version.V11,
        RuleType.Flag,
    ) { c: AnalyzerConfig, v -> c.copy(restrictReadInputArguments = v) }

val LINT_RULES: List<Rule<AnalyzerConfig, *>> = listOf(IDENTIFIER_NAMING, PRINTLN_ARGUMENT, READ_INPUT_ARGUMENT)

val DEPRECATED_LINT_KEYS: List<DeprecatedKey<AnalyzerConfig>> =
    listOf(
        DeprecatedKey.Mapped(
            "identifier_format",
            IDENTIFIER_NAMING,
            mapOf("camel case" to "camel-case", "snake case" to "snake-case"),
        ),
        DeprecatedKey.Mapped(
            "mandatory-variable-or-literal-in-println",
            PRINTLN_ARGUMENT,
            mapOf(true to true, false to null),
        ),
        DeprecatedKey.Mapped(
            "mandatory-variable-or-literal-in-readInput",
            READ_INPUT_ARGUMENT,
            mapOf(true to true, false to null),
        ),
    )

val LINT_RULE_SET = RuleSet(LINT_RULES, DEPRECATED_LINT_KEYS, AnalyzerConfig(), "lint")
