package snippetsearcher.printscript.formatting

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import snippetsearcher.printscript.common.Limits
import snippetsearcher.printscript.common.deeplyNestedCode
import tools.jackson.databind.json.JsonMapper

private const val MAX_CODE_SIZE = 64 * 1024

// Con el service real: no tiene estado ni dependencias, y así se prueba la librería de punta a punta
@WebMvcTest(FormattingController::class)
// Sin los filtros de seguridad: este test prueba el controller. La seguridad está en SecurityConfigurationTest
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(Limits::class)
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
    fun `un código con mucho anidamiento responde 200 con el código formateado`() {
        withCode(deeplyNestedCode).andExpect {
            status { isOk() }
            content {
                json("""{"formatted":"$deeplyNestedCode","error":null,"ignoredRules":[]}""", JsonCompareMode.STRICT)
            }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        format("30-para-formatear.ps", "2.0", emptyMap()).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `una regla desconocida responde 400 con el motivo`() {
        format("30-para-formatear.ps", "1.1", mapOf("no-existe" to true)).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de formato desconocida: 'no-existe'") }
            jsonPath("$.rule") { value("no-existe") }
        }
    }

    @Test
    fun `una clave vieja de la librería responde 400 como regla desconocida`() {
        format("30-para-formatear.ps", "1.1", mapOf("enforce-spacing-around-equals" to true)).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de formato desconocida: 'enforce-spacing-around-equals'") }
            jsonPath("$.rule") { value("enforce-spacing-around-equals") }
        }
    }

    @Test
    fun `una regla con el tipo incorrecto responde 400 con el motivo`() {
        format("30-para-formatear.ps", "1.1", mapOf("if-indent" to true)).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("La regla 'if-indent' espera un número entero") }
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
    fun `sin rules en el body responde 400`() {
        post("""{"code":"println(1);","version":"1.0"}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `sin code en el body responde 400`() {
        post("""{"version":"1.0","rules":{}}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `un code de más de 64 KB responde 413 con el motivo`() {
        withCode(" ".repeat(MAX_CODE_SIZE + 1)).andExpect {
            status { isEqualTo(413) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Payload Too Large") }
            jsonPath("$.detail") { value("El código supera el tamaño máximo de 64 KB") }
        }
    }

    @Test
    fun `un code de exactamente 64 KB no responde 413`() {
        withCode(" ".repeat(MAX_CODE_SIZE)).andExpect { status { isOk() } }
    }

    private fun withCode(code: String) =
        post(json.writeValueAsString(mapOf("code" to code, "version" to "1.1", "rules" to emptyMap<String, Any>())))

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
