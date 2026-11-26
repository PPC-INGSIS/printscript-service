package snippetsearcher.printscript.execution

import org.springframework.web.socket.CloseStatus
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Lo bastante largo para no fallar en una máquina lenta: solo se espera entero si hay un bug
private const val TIMEOUT_SECONDS = 5L

private val json = JsonMapper()
private val httpClient = HttpClient.newHttpClient()

sealed interface Received {
    data class Message(
        val body: JsonNode,
    ) : Received

    data class Closed(
        val code: Int,
    ) : Received
}

fun message(vararg fields: Pair<String, Any?>): Received = Received.Message(json.valueToTree(mapOf(*fields)))

val inputRequest = message("type" to "input_request")

fun output(text: String) = message("type" to "output", "text" to text)

fun closed(status: CloseStatus): Received = Received.Closed(status.code)

fun assertRejected(
    socket: TestSocket,
    detail: String,
    status: CloseStatus,
) {
    assertEquals(message("type" to "rejected", "detail" to detail), socket.next())
    assertEquals(closed(status), socket.next())
}

fun snippet(name: String): String = TestSocket::class.java.getResource("/snippets/$name")!!.readText()

// El cliente del JDK alcanza para los tests: no suma una dependencia
class TestSocket private constructor(
    private val reading: Boolean,
) : WebSocket.Listener {
    private val received = LinkedBlockingQueue<Received>()
    private val partial = StringBuilder()
    private lateinit var socket: WebSocket

    // Sin pedir mensajes, el cliente del JDK deja de leer del socket: así se arma un cliente que no lee
    override fun onOpen(webSocket: WebSocket) {
        if (reading) webSocket.request(1)
    }

    override fun onText(
        webSocket: WebSocket,
        data: CharSequence,
        last: Boolean,
    ): CompletionStage<*>? {
        partial.append(data)
        if (last) {
            received.put(Received.Message(json.readTree(partial.toString())))
            partial.clear()
        }
        webSocket.request(1)
        return null
    }

    override fun onClose(
        webSocket: WebSocket,
        statusCode: Int,
        reason: String,
    ): CompletionStage<*>? {
        received.put(Received.Closed(statusCode))
        return null
    }

    fun send(vararg fields: Pair<String, Any?>) = sendText(json.writeValueAsString(mapOf(*fields)))

    fun sendText(text: String) {
        socket.sendText(text, true).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun next(): Received =
        assertNotNull(
            received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            "No llegó el mensaje esperado",
        )

    fun close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun abort() = socket.abort()

    companion object {
        fun open(
            port: Int,
            token: String? = "token-de-prueba",
            reading: Boolean = true,
        ): TestSocket {
            val listener = TestSocket(reading)
            val builder = httpClient.newWebSocketBuilder()
            token?.let { builder.header("Authorization", "Bearer $it") }
            listener.socket =
                builder
                    .buildAsync(URI("ws://localhost:$port/execute/interactive"), listener)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return listener
        }
    }
}
