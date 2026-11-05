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
    private val formattedWithIfIndent =
        """{"formatted":"let x:number = 1;\nif (activo) {\n    println(x);\n}",""" +
            """"error":null,"ignoredRules":[]}"""

    @Test
    fun `con reglas válidas responde 200 con el código formateado`() {
        val rules = mapOf("space-around-assignment" to "always", "if-indent" to 4)

        format("30-para-formatear.ps", "1.1", rules).andExpect {
            status { isOk() }
            header { doesNotExist("Deprecation") }
            content { json(formattedWithIfIndent, JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `con claves deprecadas responde 200 con el mismo formateado y el header Deprecation`() {
        val rules = mapOf("enforce-spacing-around-equals" to true, "indent-inside-if" to 4)

        format("30-para-formatear.ps", "1.1", rules).andExpect {
            status { isOk() }
            header { string("Deprecation", "@1793836800") }
            content { json(formattedWithIfIndent, JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `una regla de una versión posterior se ignora y se informa en ignoredRules`() {
        format("10-hola-mundo.ps", "1.0", mapOf("if-indent" to 4)).andExpect {
            status { isOk() }
            jsonPath("$.formatted") { value(snippet("10-hola-mundo.ps").trimEnd()) }
            content { json("""{"ignoredRules":["if-indent"]}""", JsonCompareMode.LENIENT) }
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
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
            jsonPath("$.message") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `una regla desconocida responde 400 con el motivo`() {
        format("30-para-formatear.ps", "1.1", mapOf("no-existe" to true)).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de formato desconocida: 'no-existe'") }
            jsonPath("$.message") { value("Regla de formato desconocida: 'no-existe'") }
            jsonPath("$.rule") { value("no-existe") }
        }
    }

    @Test
    fun `una regla con el tipo incorrecto responde 400 con el motivo`() {
        format("30-para-formatear.ps", "1.1", mapOf("if-indent" to true)).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("La regla 'if-indent' espera un número entero") }
            jsonPath("$.message") { value("La regla 'if-indent' espera un número entero") }
            jsonPath("$.rule") { value("if-indent") }
        }
    }

    @Test
    fun `un entero que no entra en un Int responde 400 por fuera de rango`() {
        post("""{"code":"println(1);","version":"1.1","rules":{"if-indent":3000000000}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("La regla 'if-indent' acepta valores entre 0 y 8, se recibió 3000000000") }
            jsonPath("$.rule") { value("if-indent") }
        }
    }

    @Test
    fun `dos claves con valores distintos para la misma regla responden 400 con el motivo`() {
        val rules = mapOf("enforce-spacing-around-equals" to true, "enforce-no-spacing-around-equals" to true)
        val detail =
            "'enforce-spacing-around-equals' y 'enforce-no-spacing-around-equals' " +
                "definen 'space-around-assignment' con valores distintos"

        format("30-para-formatear.ps", "1.1", rules).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value(detail) }
            jsonPath("$.message") { value(detail) }
            jsonPath("$.rule") { value("space-around-assignment") }
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
