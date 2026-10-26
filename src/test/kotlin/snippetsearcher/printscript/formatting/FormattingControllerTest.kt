package snippetsearcher.printscript.formatting

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

// Con el service real: no tiene estado ni dependencias, y así se prueba la librería de punta a punta
@WebMvcTest(FormattingController::class)
@Import(FormattingService::class)
class FormattingControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val json = JsonMapper()

    @Test
    fun `con reglas válidas responde 200 con el código formateado`() {
        val rules = mapOf("enforce-spacing-around-equals" to true, "indent-inside-if" to 4)

        format("30-para-formatear.ps", "1.1", rules).andExpect {
            status { isOk() }
            content {
                json(
                    """{"formatted":"let x:number = 1;\nif (activo) {\n    println(x);\n}","error":null}""",
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `un error en el código responde 200 con el error y sin formateado`() {
        format("con-error-lexico.ps", "1.0", emptyMap()).andExpect {
            status { isOk() }
            jsonPath("$.formatted") { value(null) }
            jsonPath("$.error.message") { isNotEmpty() }
            jsonPath("$.error.line") { value(2) }
            jsonPath("$.error.column") { value(19) }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        format("30-para-formatear.ps", "2.0", emptyMap()).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `una regla desconocida responde 400 con el motivo`() {
        format("30-para-formatear.ps", "1.1", mapOf("no-existe" to true)).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Unknown formatting rule: 'no-existe'") }
        }
    }

    @Test
    fun `una regla con el tipo incorrecto responde 400 con el motivo`() {
        format("30-para-formatear.ps", "1.1", mapOf("indent-inside-if" to true)).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Rule 'indent-inside-if' expects a int value") }
        }
    }

    @Test
    fun `sin rules en el body responde 400`() {
        post("""{"code":"println(1);","version":"1.0"}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `sin code en el body responde 400`() {
        post("""{"version":"1.0","rules":{}}""").andExpect { status { isBadRequest() } }
    }

    private fun format(
        name: String,
        version: String,
        rules: Map<String, Any>,
    ) = post(json.writeValueAsString(mapOf("code" to snippet(name), "version" to version, "rules" to rules)))

    private fun post(body: String) =
        mockMvc.post("/format") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
