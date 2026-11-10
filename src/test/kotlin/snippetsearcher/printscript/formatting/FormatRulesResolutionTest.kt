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
    fun `cada clave deprecada produce lo mismo que su regla`() {
        val cases =
            listOf(
                ("enforce-spacing-before-colon-in-declaration" to true) to ("space-before-colon" to "always"),
                ("enforce-spacing-before-colon-in-declaration" to false) to ("space-before-colon" to "never"),
                ("enforce-spacing-after-colon-in-declaration" to true) to ("space-after-colon" to "always"),
                ("enforce-spacing-after-colon-in-declaration" to false) to ("space-after-colon" to "never"),
                ("enforce-spacing-around-equals" to true) to ("space-around-assignment" to "always"),
                ("enforce-spacing-around-equals" to false) to ("space-around-assignment" to "never"),
                ("enforce-no-spacing-around-equals" to true) to ("space-around-assignment" to "never"),
                ("enforce-no-spacing-around-equals" to false) to ("space-around-assignment" to "always"),
                ("mandatory-line-break-after-statement" to true) to ("line-break-after-statement" to true),
                ("mandatory-line-break-after-statement" to false) to null,
                ("mandatory-space-surrounding-operations" to true) to ("space-around-operators" to true),
                ("mandatory-space-surrounding-operations" to false) to null,
                ("mandatory-single-space-separation" to true) to ("single-space-separation" to true),
                ("mandatory-single-space-separation" to false) to null,
                ("if-brace-same-line" to true) to ("if-brace-position" to "same-line"),
                ("if-brace-same-line" to false) to null,
                ("if-brace-below-line" to true) to ("if-brace-position" to "next-line"),
                ("if-brace-below-line" to false) to null,
                ("line-breaks-after-println" to 1) to ("blank-lines-after-println" to 1),
                ("indent-inside-if" to 4) to ("if-indent" to 4),
            )

        for ((deprecated, rule) in cases) {
            val expected = resolve(listOfNotNull(rule).toMap()) as RulesResolution.Supported<FormatterConfig>

            assertEquals(
                expected.copy(deprecatedKeys = listOf(deprecated.first)),
                resolve(mapOf(deprecated)),
                "$deprecated",
            )
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
                ("if-brace-same-line" to "sí") to "La regla 'if-brace-same-line' tiene que ser true o false",
                ("indent-inside-if" to true) to "La regla 'indent-inside-if' espera un número entero",
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
                ("indent-inside-if" to -1) to "La regla 'indent-inside-if' acepta valores entre 0 y 8, se recibió -1",
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
    fun `dos claves que definen la misma regla con valores distintos son inválidas`() {
        val rules = mapOf("enforce-spacing-around-equals" to true, "enforce-no-spacing-around-equals" to true)

        assertEquals(
            invalid(
                "'enforce-spacing-around-equals' y 'enforce-no-spacing-around-equals' " +
                    "definen 'space-around-assignment' con valores distintos",
                "space-around-assignment",
            ),
            resolve(rules),
        )
    }

    @Test
    fun `dos claves que definen la misma regla con el mismo valor no son un conflicto`() {
        val rules = mapOf("enforce-spacing-around-equals" to true, "space-around-assignment" to "always")

        assertEquals(
            supported(
                FormatterConfig(spaceAroundAssignment = Spacing.SINGLE),
                deprecatedKeys = listOf("enforce-spacing-around-equals"),
            ),
            resolve(rules),
        )
    }

    @Test
    fun `un flag en false no choca con otra clave que lo activa`() {
        val rules = mapOf("line-break-after-statement" to false, "mandatory-line-break-after-statement" to true)

        assertEquals(
            supported(
                FormatterConfig(lineBreakAfterStatement = true),
                deprecatedKeys = listOf("mandatory-line-break-after-statement"),
            ),
            resolve(rules),
        )
    }

    @Test
    fun `una regla de una versión posterior se ignora y se informa`() {
        val rules = mapOf("if-indent" to 4, "indent-inside-if" to 4, "space-before-colon" to "always")

        assertEquals(
            supported(
                FormatterConfig(spaceBeforeColon = Spacing.SINGLE),
                ignoredRules = listOf("if-indent"),
                deprecatedKeys = listOf("indent-inside-if"),
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
    fun `informa solo las claves deprecadas usadas`() {
        val rules = mapOf("if-brace-same-line" to false, "if-indent" to 2, "line-breaks-after-println" to 0)

        val resolution = resolve(rules) as RulesResolution.Supported<FormatterConfig>

        assertEquals(listOf("if-brace-same-line", "line-breaks-after-println"), resolution.deprecatedKeys)
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
        deprecatedKeys: List<String> = emptyList(),
    ) = RulesResolution.Supported(config, ignoredRules, deprecatedKeys)
}
