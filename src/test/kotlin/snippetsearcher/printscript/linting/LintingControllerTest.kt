package snippetsearcher.printscript.linting

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
@WebMvcTest(LintingController::class)
@Import(LintingService::class)
class LintingControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val json = JsonMapper()
    private val violations =
        """
        {"violations":[
          {"rule":"identifier-naming",
           "message":"El identificador 'mi_variable' no sigue la convención de nombres configurada.",
           "line":1,"column":5,"severity":"WARNING"},
          {"rule":"println-argument",
           "message":"println solo admite un identificador o un literal, no una expresión.",
           "line":3,"column":9,"severity":"ERROR"}
        ],"ignoredRules":[]}
        """

    @Test
    fun `sin violaciones responde 200 con la lista vacía`() {
        lint("40-para-lintear.ps", "1.1", emptyMap()).andExpect {
            status { isOk() }
            header { doesNotExist("Deprecation") }
            content { json("""{"violations":[],"ignoredRules":[]}""", JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `con violaciones responde 200 con cada una y su posición`() {
        val rules = mapOf("identifier-naming" to "camel-case", "println-argument" to true)

        lint("40-para-lintear.ps", "1.1", rules).andExpect {
            status { isOk() }
            header { doesNotExist("Deprecation") }
            content { json(violations, JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `con claves deprecadas responde 200 con las mismas violaciones y el header Deprecation`() {
        val rules = mapOf("identifier_format" to "camel case", "mandatory-variable-or-literal-in-println" to true)

        lint("40-para-lintear.ps", "1.1", rules).andExpect {
            status { isOk() }
            header { string("Deprecation", "@1793836800") }
            content { json(violations, JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `read-input-argument en un snippet 1_0 se ignora y se informa en ignoredRules`() {
        lint("10-hola-mundo.ps", "1.0", mapOf("read-input-argument" to true)).andExpect {
            status { isOk() }
            content { json("""{"violations":[],"ignoredRules":["read-input-argument"]}""", JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        lint("40-para-lintear.ps", "2.0", emptyMap()).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
            jsonPath("$.message") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `una regla desconocida responde 400 con el motivo`() {
        lint("40-para-lintear.ps", "1.1", mapOf("no-existe" to true)).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de lint desconocida: 'no-existe'") }
            jsonPath("$.message") { value("Regla de lint desconocida: 'no-existe'") }
            jsonPath("$.rule") { value("no-existe") }
        }
    }

    @Test
    fun `un identifier-naming inválido responde 400 con el motivo y la regla`() {
        val message = "La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'"

        lint("40-para-lintear.ps", "1.1", mapOf("identifier-naming" to "pascal-case")).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value(message) }
            jsonPath("$.message") { value(message) }
            jsonPath("$.rule") { value("identifier-naming") }
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

    private fun lint(
        name: String,
        version: String,
        rules: Map<String, Any>,
    ) = post(json.writeValueAsString(mapOf("code" to snippet(name), "version" to version, "rules" to rules)))

    private fun post(body: String) =
        mockMvc.post("/lint") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
