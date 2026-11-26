package snippetsearcher.printscript.execution

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import snippetsearcher.printscript.common.InteractiveLimits
import snippetsearcher.printscript.common.Limits
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.resolveVersion
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val CONNECTION = "connection"

@Component
class InteractiveExecutionHandler(
    private val limits: Limits,
    private val interactiveLimits: InteractiveLimits,
    private val executions: InteractiveExecutionFactory,
    private val json: JsonMapper,
) : TextWebSocketHandler() {
    private val openSessions = AtomicInteger()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        // Se cuenta antes que nada: afterConnectionClosed descuenta siempre, también a las rechazadas
        val open = openSessions.incrementAndGet()
        val connection = Connection(session)
        session.attributes[CONNECTION] = connection
        session.textMessageSizeLimit = interactiveLimits.maxMessageSize.toBytes().toInt()
        val max = interactiveLimits.maxSessions
        if (open > max) {
            connection.reject(
                "Se alcanzó el máximo de conexiones abiertas a la vez ($max). Probá de nuevo más tarde",
                CloseStatus.SERVICE_OVERLOAD,
            )
        }
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        // Tomcat sigue entregando lo que el cliente mandó antes de ver el cierre: sin esto, un start detrás
        // de un rechazo arrancaría una ejecución que ya nadie cancela
        if (!session.isOpen) return
        val connection = connection(session)
        when (val request = parse(message.payload)) {
            is ClientMessage.Invalid -> connection.reject(request.detail)
            is ClientMessage.Start -> start(connection, request)
            is ClientMessage.Input -> input(connection, request)
        }
    }

    // Si la ejecución ya terminó, cancelar no hace nada
    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        openSessions.decrementAndGet()
        connection(session).execution?.cancel()
    }

    private fun start(
        connection: Connection,
        request: ClientMessage.Start,
    ) {
        if (connection.execution != null) {
            connection.reject("La ejecución ya arrancó: no se puede mandar otro 'start'")
        } else if (limits.isCodeTooLarge(request.code)) {
            connection.reject(limits.codeTooLargeMessage, CloseStatus.TOO_BIG_TO_PROCESS)
        } else {
            when (val resolution = resolveVersion(request.version)) {
                is VersionResolution.Unsupported -> connection.reject(resolution.message)
                is VersionResolution.Supported -> {
                    val execution = executions.create(request.code, resolution.version, connection::emit)
                    // Se guarda antes de arrancar: emit la usa para cancelar si un envío falla
                    connection.execution = execution
                    execution.start()
                }
            }
        }
    }

    private fun input(
        connection: Connection,
        request: ClientMessage.Input,
    ) {
        val execution = connection.execution
        if (execution == null) {
            connection.reject("El primer mensaje tiene que ser 'start'")
        } else if (!execution.provideInput(request.value)) {
            connection.reject("Hay demasiados inputs sin consumir: el máximo es ${interactiveLimits.maxPendingInputs}")
        }
    }

    private fun parse(payload: String): ClientMessage {
        val node =
            try {
                json.readTree(payload)
            } catch (expected: JacksonException) {
                return ClientMessage.Invalid("El mensaje no es un JSON válido")
            }
        return when (val type = node.text("type")) {
            "start" -> parseStart(node)
            "input" -> node.text("value")?.let { ClientMessage.Input(it) } ?: missing("input", "value")
            null -> ClientMessage.Invalid("Falta el campo 'type'")
            else -> ClientMessage.Invalid("Tipo de mensaje desconocido: '$type'. Se espera 'start' o 'input'")
        }
    }

    private fun parseStart(node: JsonNode): ClientMessage {
        val code = node.text("code") ?: return missing("start", "code")
        val version = node.text("version") ?: return missing("start", "version")
        return ClientMessage.Start(code, version)
    }

    // Un campo con otro tipo cuenta como faltante
    private fun JsonNode.text(field: String): String? = get(field)?.takeIf { it.isString }?.stringValue()

    private fun missing(
        type: String,
        field: String,
    ) = ClientMessage.Invalid("El mensaje '$type' necesita el campo '$field' como texto")

    private fun connection(session: WebSocketSession) = session.attributes[CONNECTION] as Connection

    private inner class Connection(
        private val session: WebSocketSession,
    ) {
        @Volatile
        var execution: InteractiveExecution? = null

        // Los eventos salen del thread del snippet y los rejected del contenedor: el lock hace que no envíen a la vez
        // y que después de un cierre no salga nada más
        private val sending = ReentrantLock()

        // Nunca lanza: si lanzara, el núcleo loguearía cada desconexión como un error inesperado.
        // Si el envío falla, la conexión ya no sirve: se cancela la ejecución en vez de propagar el error.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        fun emit(event: ExecutionEvent) {
            try {
                sending.withLock {
                    if (!session.isOpen) return
                    send(event.toMessage())
                    if (event is ExecutionEvent.Finished) session.close(CloseStatus.NORMAL)
                }
            } catch (failed: Exception) {
                execution?.cancel()
            }
        }

        // Si la ejecución ya cerró la sesión, no hay a quién avisarle. Si el envío falla, la conexión ya no sirve.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        fun reject(
            detail: String,
            status: CloseStatus = CloseStatus.POLICY_VIOLATION,
        ) {
            try {
                sending.withLock {
                    if (!session.isOpen) return
                    send(ServerMessage.Rejected(detail))
                    session.close(status)
                }
            } catch (failed: Exception) {
                execution?.cancel()
            }
        }

        private fun send(message: ServerMessage) = session.sendMessage(TextMessage(json.writeValueAsString(message)))
    }
}

private sealed interface ClientMessage {
    data class Start(
        val code: String,
        val version: String,
    ) : ClientMessage

    data class Input(
        val value: String,
    ) : ClientMessage

    data class Invalid(
        val detail: String,
    ) : ClientMessage
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
private sealed interface ServerMessage {
    @JsonTypeName("output")
    data class Output(
        val text: String,
    ) : ServerMessage

    @JsonTypeName("input_request")
    data object InputRequest : ServerMessage

    @JsonTypeName("finished")
    data class Finished(
        val error: SourceError?,
    ) : ServerMessage

    @JsonTypeName("rejected")
    data class Rejected(
        val detail: String,
    ) : ServerMessage
}

private fun ExecutionEvent.toMessage(): ServerMessage =
    when (this) {
        is ExecutionEvent.Output -> ServerMessage.Output(text)
        ExecutionEvent.InputRequested -> ServerMessage.InputRequest
        is ExecutionEvent.Finished -> ServerMessage.Finished(error)
    }
