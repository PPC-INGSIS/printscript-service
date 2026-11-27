package snippetsearcher.printscript.rules

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(RulesController::class)
// Sin los filtros de seguridad: este test prueba el controller. La seguridad está en SecurityConfigurationTest
@AutoConfigureMockMvc(addFilters = false)
class RulesControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `sin versión responde las reglas de formato de la última, con su schema`() {
        mockMvc.get("/rules/format").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            content {
                json(
                    """
                    {"version":"1.1","rules":[
                      {"id":"space-before-colon","description":"Espacio antes del ':' en una declaración","since":"1.0",
                       "schema":{"type":"string","enum":["always","never"],"default":"never"}},
                      {"id":"space-after-colon","description":"Espacio después del ':' en una declaración","since":"1.0",
                       "schema":{"type":"string","enum":["always","never"],"default":"always"}},
                      {"id":"space-around-assignment","description":"Espacios alrededor del '='","since":"1.0",
                       "schema":{"type":"string","enum":["always","never"],"default":"always"}},
                      {"id":"blank-lines-after-println","description":"Líneas en blanco después de cada println",
                       "since":"1.0","schema":{"type":"integer","minimum":0,"maximum":2,"default":0}},
                      {"id":"line-break-after-statement","description":"Salto de línea después de cada sentencia",
                       "since":"1.0","schema":{"type":"boolean","const":true}},
                      {"id":"space-around-operators","description":"Espacios alrededor de los operadores","since":"1.0",
                       "schema":{"type":"boolean","const":true}},
                      {"id":"single-space-separation","description":"Un solo espacio entre elementos","since":"1.0",
                       "schema":{"type":"boolean","const":true}},
                      {"id":"if-brace-position","description":"Dónde va la llave que abre un if","since":"1.1",
                       "schema":{"type":"string","enum":["same-line","next-line"],"default":"same-line"}},
                      {"id":"if-indent","description":"Espacios de indentación dentro de un if","since":"1.1",
                       "schema":{"type":"integer","minimum":0,"maximum":8,"default":4}}
                    ]}
                    """,
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `con versión 1_0 no incluye las reglas de formato del if`() {
        mockMvc.get("/rules/format?version=1.0").andExpect {
            status { isOk() }
            jsonPath("$.version") { value("1.0") }
            jsonPath("$.rules.length()") { value(7) }
            jsonPath("$.rules[?(@.id == 'if-brace-position')]") { doesNotExist() }
            jsonPath("$.rules[?(@.id == 'if-indent')]") { doesNotExist() }
        }
    }

    @Test
    fun `sin versión responde las reglas de lint de la última`() {
        mockMvc.get("/rules/lint").andExpect {
            status { isOk() }
            content {
                json(
                    """
                    {"version":"1.1","rules":[
                      {"id":"identifier-naming","description":"Convención de nombres de variables","since":"1.0",
                       "schema":{"type":"string","enum":["camel-case","snake-case"],"default":"camel-case"}},
                      {"id":"println-argument","description":"println solo recibe una variable o un literal",
                       "since":"1.0","schema":{"type":"boolean","const":true}},
                      {"id":"read-input-argument","description":"readInput solo recibe una variable o un literal",
                       "since":"1.1","schema":{"type":"boolean","const":true}}
                    ]}
                    """,
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `con versión 1_0 no incluye read-input-argument`() {
        mockMvc.get("/rules/lint?version=1.0").andExpect {
            status { isOk() }
            jsonPath("$.version") { value("1.0") }
            jsonPath("$.rules.length()") { value(2) }
            jsonPath("$.rules[?(@.id == 'read-input-argument')]") { doesNotExist() }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        for (path in listOf("/rules/format", "/rules/lint")) {
            mockMvc.get("$path?version=2.0").andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.detail") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
            }
        }
    }

    @Test
    fun `reglas de formato válidas responden 204 sin body`() {
        validate("/rules/format/validate", """{"rules":{"space-before-colon":"never","blank-lines-after-println":2}}""")
            .andExpect {
                status { isNoContent() }
                content { string("") }
            }
    }

    @Test
    fun `sin reglas de formato responde 204`() {
        validate("/rules/format/validate", """{"rules":{}}""").andExpect { status { isNoContent() } }
    }

    @Test
    fun `una regla de formato desconocida responde 400 con el motivo y la regla`() {
        validate("/rules/format/validate", """{"rules":{"no-existe":true}}""").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de formato desconocida: 'no-existe'") }
            jsonPath("$.rule") { value("no-existe") }
        }
    }

    @Test
    fun `una opción de formato que no existe responde 400 con la regla`() {
        validate("/rules/format/validate", """{"rules":{"space-before-colon":"a-veces"}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("La regla 'space-before-colon' tiene que ser 'always' o 'never'") }
            jsonPath("$.rule") { value("space-before-colon") }
        }
    }

    @Test
    fun `un entero de formato fuera de rango responde 400 con la regla`() {
        validate("/rules/format/validate", """{"rules":{"if-indent":12}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("La regla 'if-indent' acepta valores entre 0 y 8, se recibió 12") }
            jsonPath("$.rule") { value("if-indent") }
        }
    }

    @Test
    fun `un flag de formato en false responde 204`() {
        validate("/rules/format/validate", """{"rules":{"line-break-after-statement":false}}""")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `una regla de formato de la 1_1 responde 204 sin mandar versión`() {
        validate("/rules/format/validate", """{"rules":{"if-indent":4}}""").andExpect { status { isNoContent() } }
    }

    @Test
    fun `validar formato sin rules en el body responde 400`() {
        validate("/rules/format/validate", "{}").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.rule") { doesNotExist() }
        }
    }

    @Test
    fun `reglas de lint válidas responden 204 sin body`() {
        validate("/rules/lint/validate", """{"rules":{"identifier-naming":"snake-case","println-argument":true}}""")
            .andExpect {
                status { isNoContent() }
                content { string("") }
            }
    }

    @Test
    fun `sin reglas de lint responde 204`() {
        validate("/rules/lint/validate", """{"rules":{}}""").andExpect { status { isNoContent() } }
    }

    @Test
    fun `una regla de lint desconocida responde 400 con el motivo y la regla`() {
        validate("/rules/lint/validate", """{"rules":{"no-existe":true}}""").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Regla de lint desconocida: 'no-existe'") }
            jsonPath("$.rule") { value("no-existe") }
        }
    }

    @Test
    fun `una opción de lint que no existe responde 400 con la regla`() {
        validate("/rules/lint/validate", """{"rules":{"identifier-naming":"kebab-case"}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'") }
            jsonPath("$.rule") { value("identifier-naming") }
        }
    }

    @Test
    fun `un flag de lint en false responde 204`() {
        validate("/rules/lint/validate", """{"rules":{"println-argument":false}}""")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `una regla de lint de la 1_1 responde 204 sin mandar versión`() {
        validate("/rules/lint/validate", """{"rules":{"read-input-argument":true}}""")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `validar lint sin rules en el body responde 400`() {
        validate("/rules/lint/validate", "{}").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.rule") { doesNotExist() }
        }
    }

    private fun validate(
        path: String,
        body: String,
    ) = mockMvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }
}
