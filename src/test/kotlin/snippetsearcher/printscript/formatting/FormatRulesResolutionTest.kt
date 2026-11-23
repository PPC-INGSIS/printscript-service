package snippetsearcher.printscript.formatting

import org.junit.jupiter.api.Test
import org.printscript.common.Version
import org.printscript.formatter.config.BlankLines
import org.printscript.formatter.config.BracePosition
import org.printscript.formatter.config.FormatterConfig
import org.printscript.formatter.config.Indent
import org.printscript.formatter.config.Spacing
import snippetsearcher.printscript.common.RuleType
import snippetsearcher.printscript.common.RulesResolution
import snippetsearcher.printscript.common.resolveRules
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FormatRulesResolutionTest {
    @Test
    fun `sin reglas aplica la configuración por defecto`() {
        assertEquals(supported(FormatterConfig()), resolve(emptyMap()))
    }

    @Test
    fun `cada regla produce su campo de la configuración`() {
        val cases =
            listOf(
                ("space-before-colon" to "always") to FormatterConfig(spaceBeforeColon = Spacing.SINGLE),
                ("space-before-colon" to "never") to FormatterConfig(spaceBeforeColon = Spacing.NONE),
                ("space-after-colon" to "always") to FormatterConfig(spaceAfterColon = Spacing.SINGLE),
                ("space-after-colon" to "never") to FormatterConfig(spaceAfterColon = Spacing.NONE),
                ("space-around-assignment" to "always") to FormatterConfig(spaceAroundAssignment = Spacing.SINGLE),
                ("space-around-assignment" to "never") to FormatterConfig(spaceAroundAssignment = Spacing.NONE),
                ("blank-lines-after-println" to 2) to FormatterConfig(lineBreaksAfterPrintln = BlankLines.TWO),
                ("line-break-after-statement" to true) to FormatterConfig(lineBreakAfterStatement = true),
                ("space-around-operators" to true) to FormatterConfig(spaceSurroundingOperations = true),
                ("single-space-separation" to true) to FormatterConfig(singleSpaceSeparation = true),
                ("if-brace-position" to "same-line") to FormatterConfig(ifBrace = BracePosition.SAME_LINE),
                ("if-brace-position" to "next-line") to FormatterConfig(ifBrace = BracePosition.BELOW_LINE),
                ("if-indent" to 4) to FormatterConfig(indentInsideIf = Indent.of(4)),
            )

        for ((rule, config) in cases) {
            assertEquals(supported(config), resolve(mapOf(rule)), "$rule")
        }
    }

    @Test
    fun `un flag en false equivale a no mandarlo`() {
        val rules = mapOf("line-break-after-statement" to false)

        assertEquals(supported(FormatterConfig()), resolve(rules))
    }

    @Test
    fun `una regla desconocida es inválida`() {
        assertEquals(
            invalid("Regla de formato desconocida: 'no-existe'", "no-existe"),
            resolve(mapOf("no-existe" to true)),
        )
    }

    @Test
    fun `un valor del tipo incorrecto es inválido`() {
        val cases =
            listOf(
                ("space-before-colon" to true) to "La regla 'space-before-colon' tiene que ser 'always' o 'never'",
                ("if-brace-position" to "below") to
                    "La regla 'if-brace-position' tiene que ser 'same-line' o 'next-line'",
                ("if-indent" to true) to "La regla 'if-indent' espera un número entero",
                ("if-indent" to "4") to "La regla 'if-indent' espera un número entero",
                ("if-indent" to 4.5) to "La regla 'if-indent' espera un número entero",
                ("line-break-after-statement" to "true") to "La regla 'line-break-after-statement' solo acepta true",
            )

        for ((rule, message) in cases) {
            assertEquals(invalid(message, rule.first), resolve(mapOf(rule)))
        }
    }

    @Test
    fun `un entero fuera de rango es inválido`() {
        val cases =
            listOf(
                ("blank-lines-after-println" to 3) to
                    "La regla 'blank-lines-after-println' acepta valores entre 0 y 2, se recibió 3",
                ("if-indent" to 9) to "La regla 'if-indent' acepta valores entre 0 y 8, se recibió 9",
                ("if-indent" to 3_000_000_000L) to
                    "La regla 'if-indent' acepta valores entre 0 y 8, se recibió 3000000000",
                ("if-indent" to BigInteger("10000000000000000000")) to
                    "La regla 'if-indent' acepta valores entre 0 y 8, se recibió 10000000000000000000",
            )

        for ((rule, message) in cases) {
            assertEquals(invalid(message, rule.first), resolve(mapOf(rule)))
        }
    }

    @Test
    fun `una regla de una versión posterior se ignora y se informa`() {
        val rules = mapOf("if-indent" to 4, "space-before-colon" to "always")

        assertEquals(
            supported(
                FormatterConfig(spaceBeforeColon = Spacing.SINGLE),
                ignoredRules = listOf("if-indent"),
            ),
            resolve(rules, Version.V10),
        )
    }

    @Test
    fun `una regla de una versión posterior con un valor inválido es inválida`() {
        assertEquals(
            invalid("La regla 'if-indent' acepta valores entre 0 y 8, se recibió 12", "if-indent"),
            resolve(mapOf("if-indent" to 12), Version.V10),
        )
    }

    @Test
    fun `cada regla del catálogo tiene un default válido, salvo los flags que no tienen`() {
        for (rule in FORMAT_RULES) {
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
    ) = resolveRules(rules, version, FORMAT_RULE_SET)

    private fun invalid(
        message: String,
        rule: String,
    ) = RulesResolution.Invalid(message, rule)

    private fun supported(
        config: FormatterConfig,
        ignoredRules: List<String> = emptyList(),
    ) = RulesResolution.Supported(config, ignoredRules)
}
