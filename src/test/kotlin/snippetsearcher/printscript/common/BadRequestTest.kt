package snippetsearcher.printscript.common

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class BadRequestTest {
    @Test
    fun `badRequest arma un 400 con detail y sin message`() {
        val response = badRequest("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1")
        val body = assertNotNull(response.body)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1", body.detail)
        assertFalse(body.properties.orEmpty().containsKey("message"))
    }

    @Test
    fun `badRequest con rule arma un 400 con detail y rule, sin message`() {
        val response = badRequest("La regla 'if-indent' espera un número entero", "if-indent")
        val body = assertNotNull(response.body)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("La regla 'if-indent' espera un número entero", body.detail)
        assertEquals("if-indent", body.properties?.get("rule"))
        assertFalse(body.properties.orEmpty().containsKey("message"))
    }

    @Test
    fun `payloadTooLarge arma un 413 Payload Too Large con detail y sin message`() {
        val response = payloadTooLarge("El código supera el tamaño máximo de 64 KB")
        val body = assertNotNull(response.body)

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.statusCode)
        assertEquals("Payload Too Large", body.title)
        assertEquals("El código supera el tamaño máximo de 64 KB", body.detail)
        assertFalse(body.properties.orEmpty().containsKey("message"))
    }
}
