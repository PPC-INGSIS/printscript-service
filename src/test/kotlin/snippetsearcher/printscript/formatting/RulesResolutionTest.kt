package snippetsearcher.printscript.formatting

import org.junit.jupiter.api.Test
import org.printscript.formatter.config.BlankLines
import org.printscript.formatter.config.FormatterConfig
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

class RulesResolutionTest {
    private val json = JsonMapper()

    @Test
    fun `sin reglas aplica la configuración por defecto`() {
        assertEquals(RulesResolution.Supported(FormatterConfig()), resolveRules(emptyMap(), json))
    }

    @Test
    fun `las reglas dadas se aplican sobre los defaults`() {
        val resolution = resolveRules(mapOf("line-breaks-after-println" to 2), json)

        assertEquals(RulesResolution.Supported(FormatterConfig(lineBreaksAfterPrintln = BlankLines.TWO)), resolution)
    }

    @Test
    fun `una regla desconocida trae el motivo`() {
        assertEquals(
            RulesResolution.Invalid("Unknown formatting rule: 'no-existe'"),
            resolveRules(mapOf("no-existe" to true), json),
        )
    }

    @Test
    fun `un valor fuera de rango trae el motivo`() {
        assertEquals(
            RulesResolution.Invalid("Rule 'indent-inside-if' expects a value within 0..8, got 9"),
            resolveRules(mapOf("indent-inside-if" to 9), json),
        )
    }

    @Test
    fun `un valor que no es booleano ni entero trae el motivo`() {
        assertEquals(
            RulesResolution.Invalid("La regla 'indent-inside-if' tiene que ser un booleano o un numero entero"),
            resolveRules(mapOf("indent-inside-if" to "4"), json),
        )
    }
}
