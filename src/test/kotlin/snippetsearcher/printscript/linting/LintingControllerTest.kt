package snippetsearcher.printscript.linting

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
@WebMvcTest(LintingController::class)
// Sin los filtros de seguridad: este test prueba el controller. La seguridad está en SecurityConfigurationTest
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(Limits::class)
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
        ],"error":null,"ignoredRules":[]}
        """

    @Test
    fun `sin violaciones responde 200 con la lista vacía`() {
        lint("40-para-lintear.ps", "1.1", emptyMap()).andExpect {
            status { isOk() }
            header { doesNotExist("Deprecation") }
            content { json("""{"violations":[],"error":null,"ignoredRules":[]}""", JsonCompareMode.STRICT) }
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
    fun `un código con demasiado anidamiento responde 200 con las violaciones hasta ahí y el error`() {
        val code = "let mi_variable: number = 1;\n$deeplyNestedCode"
        val message = "El identificador 'mi_variable' no sigue la convención de nombres configurada."
        val body = mapOf("code" to code, "version" to "1.1", "rules" to mapOf("identifier-naming" to "camel-case"))

        post(json.writeValueAsString(body)).andExpect {
            status { isOk() }
            content {
                json(
                    """
                    {
                      "violations": [{
                        "rule": "identifier-naming",
                        "message": "$message",
                        "line": 1,
                        "column": 5,
                        "severity": "WARNING"
                      }],
                      "error": {
                        "message": "El código tiene demasiados niveles de anidamiento",
                        "line": null,
                        "column": null
                      },
                      "ignoredRules": []
                    }
                    """,
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `read-input-argument en un snippet 1_0 se ignora y se informa en ignoredRules`() {
        lint("10-hola-mundo.ps", "1.0", mapOf("read-input-argument" to true)).andExpect {
            status { isOk() }
            content {
                json(
                    """{"violations":[],"error":null,"ignoredRules":["read-input-argument"]}""",
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        lint("40-para-lintear.ps", "2.0", emptyMap()).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `una regla desconocida responde 400 con el motivo`() {
        lint("40-para-lintear.ps", "1.1", mapOf("no-existe" to true)).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de lint desconocida: 'no-existe'") }
            jsonPath("$.rule") { value("no-existe") }
        }
    }

    @Test
    fun `una clave vieja de la librería responde 400 como regla desconocida`() {
        lint("40-para-lintear.ps", "1.1", mapOf("identifier_format" to "camel case")).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de lint desconocida: 'identifier_format'") }
            jsonPath("$.rule") { value("identifier_format") }
        }
    }

    @Test
    fun `un identifier-naming inválido responde 400 con el motivo y la regla`() {
        val message = "La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'"

        lint("40-para-lintear.ps", "1.1", mapOf("identifier-naming" to "pascal-case")).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value(message) }
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
