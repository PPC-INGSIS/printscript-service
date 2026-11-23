package snippetsearcher.printscript.linting

import org.junit.jupiter.api.Test
import org.printscript.analyzer.config.AnalyzerConfig
import org.printscript.analyzer.config.CamelCase
import org.printscript.analyzer.config.SnakeCase
import org.printscript.common.Version
import snippetsearcher.printscript.common.RuleType
import snippetsearcher.printscript.common.RulesResolution
import snippetsearcher.printscript.common.resolveRules
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LintRulesResolutionTest {
    @Test
    fun `sin reglas aplica la configuración por defecto`() {
        assertEquals(supported(AnalyzerConfig()), resolve(emptyMap()))
    }

    @Test
    fun `cada regla produce su campo de la configuración`() {
        val cases =
            listOf(
                ("identifier-naming" to "camel-case") to AnalyzerConfig(namingConvention = CamelCase),
                ("identifier-naming" to "snake-case") to AnalyzerConfig(namingConvention = SnakeCase),
                ("println-argument" to true) to AnalyzerConfig(restrictPrintlnArguments = true),
                ("read-input-argument" to true) to AnalyzerConfig(restrictReadInputArguments = true),
            )

        for ((rule, config) in cases) {
            assertEquals(supported(config), resolve(mapOf(rule)), "$rule")
        }
    }

    @Test
    fun `un flag en false equivale a no mandarlo`() {
        val rules = mapOf("println-argument" to false, "read-input-argument" to false)

        assertEquals(supported(AnalyzerConfig()), resolve(rules))
    }

    @Test
    fun `una regla desconocida es inválida`() {
        assertEquals(
            invalid("Regla de lint desconocida: 'no-existe'", "no-existe"),
            resolve(mapOf("no-existe" to true)),
        )
    }

    @Test
    fun `un valor del tipo incorrecto es inválido`() {
        val cases =
            listOf(
                ("identifier-naming" to "pascal-case") to
                    "La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'",
                ("identifier-naming" to true) to
                    "La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'",
                ("println-argument" to "true") to "La regla 'println-argument' solo acepta true",
                ("read-input-argument" to 1) to "La regla 'read-input-argument' solo acepta true",
            )

        for ((rule, message) in cases) {
            assertEquals(invalid(message, rule.first), resolve(mapOf(rule)))
        }
    }

    @Test
    fun `read-input-argument en un snippet 1_0 se ignora y se informa`() {
        val rules = mapOf("read-input-argument" to true, "println-argument" to true)

        assertEquals(
            supported(AnalyzerConfig(restrictPrintlnArguments = true), ignoredRules = listOf("read-input-argument")),
            resolve(rules, Version.V10),
        )
    }

    @Test
    fun `una regla de una versión posterior con un valor inválido es inválida`() {
        assertEquals(
            invalid("La regla 'read-input-argument' solo acepta true", "read-input-argument"),
            resolve(mapOf("read-input-argument" to "true"), Version.V10),
        )
    }

    @Test
    fun `cada regla del catálogo tiene un default válido, salvo los flags que no tienen`() {
        for (rule in LINT_RULES) {
            if (rule.type is RuleType.Flag) {
                assertNull(rule.default, rule.id)
            } else {
                val default = assertNotNull(rule.default, rule.id)

                assertIs<RulesResolution.Supported<*>>(resolve(mapOf(rule.id to default)), rule.id)
            }
        }
    }

    private fun resolve(
        rules: Map<String, Any>,
        version: Version = Version.V11,
    ) = resolveRules(rules, version, LINT_RULE_SET)

    private fun invalid(
        message: String,
        rule: String,
    ) = RulesResolution.Invalid(message, rule)

    private fun supported(
        config: AnalyzerConfig,
        ignoredRules: List<String> = emptyList(),
    ) = RulesResolution.Supported(config, ignoredRules)
}
