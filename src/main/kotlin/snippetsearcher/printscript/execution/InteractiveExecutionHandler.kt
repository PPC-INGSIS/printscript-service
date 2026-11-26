package snippetsearcher.printscript.execution

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import jakarta.websocket.Session
import org.apache.tomcat.websocket.Constants
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.adapter.NativeWebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import snippetsearcher.printscript.common.InteractiveLimits
import snippetsearcher.printscript.common.Limits
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.describe
import snippetsearcher.printscript.common.resolveVersion
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.ScheduledFuture
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
    private val deadlines: StartDeadlines,
) : TextWebSocketHandler() {
    private val openSessions = AtomicInteger()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        // Se cuenta antes que nada: afterConnectionClosed descuenta siempre, también a las rechazadas
        val open = openSessions.incrementAndGet()
        val connection = Connection(session)
        session.attributes[CONNECTION] = connection
        session.textMessageSizeLimit = interactiveLimits.maxMessageSize.toBytes().toInt()
        session.applySendTimeout(interactiveLimits.sendTimeout)
        val max = interactiveLimits.maxSessions
        if (open > max) {
            connection.reject(
                "Se alcanzó el máximo de conexiones abiertas a la vez ($max). Probá de nuevo más tarde",
                CloseStatus.SERVICE_OVERLOAD,
            )
        } else {
            // Sin plazo, una conexión que nunca manda el start ocuparía un lugar de max-sessions hasta que
            // el cliente la cierre: con max-sessions conexiones mudas, nadie más podría ejecutar
            val timeout = interactiveLimits.startTimeout
            connection.startDeadline =
                deadlines.schedule(timeout) {
                    connection.rejectIfNotStarted("No llegó el 'start' en ${timeout.describe()}")
                }
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
        val connection = connection(session)
        connection.startDeadline?.cancel(false)
        connection.execution?.cancel()
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
                    // Si el plazo del start ya rechazó y cerró la conexión, no arranca
                    if (connection.begin(execution)) execution.start()
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
            private set

        @Volatile
        var startDeadline: ScheduledFuture<*>? = null

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

        /**
         * Guarda la ejecución antes de arrancarla, y dice si se puede arrancar.
         *
         * Se guarda antes porque emit la usa para cancelar si un envío falla. Va bajo el mismo lock que
         * `rejectIfNotStarted`: el plazo del start vence en otro thread, y sin el lock podría ver la
         * ejecución en null, el start guardarla y arrancarla, y el plazo cerrar igual la conexión.
         * Con el lock pasa una de dos: o el plazo rechazó primero y acá la sesión ya está cerrada, o
         * esto guardó la ejecución primero y el plazo no hace nada.
         */
        fun begin(started: InteractiveExecution): Boolean =
            sending.withLock {
                if (!session.isOpen) return false
                execution = started
                startDeadline?.cancel(false)
                true
            }

        fun reject(
            detail: String,
            status: CloseStatus = CloseStatus.POLICY_VIOLATION,
        ) = rejectIf(detail, status) { true }

        // El chequeo va dentro del lock, junto con el rechazo: ver begin
        fun rejectIfNotStarted(detail: String) = rejectIf(detail, CloseStatus.POLICY_VIOLATION) { execution == null }

        // Si la ejecución ya cerró la sesión, no hay a quién avisarle. Si el envío falla, la conexión ya no sirve.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun rejectIf(
            detail: String,
            status: CloseStatus,
            condition: () -> Boolean,
        ) {
            try {
                sending.withLock {
                    if (!session.isOpen || !condition()) return
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

// Tomcat lee el timeout de cada envío de las propiedades de la sesión, y solo si es un Long: si no, usa 20 s
private fun WebSocketSession.applySendTimeout(timeout: Duration) {
    val native = (this as NativeWebSocketSession).nativeSession as Session
    native.userProperties[Constants.BLOCKING_SEND_TIMEOUT_PROPERTY] = timeout.toMillis()
}

private fun ExecutionEvent.toMessage(): ServerMessage =
    when (this) {
        is ExecutionEvent.Output -> ServerMessage.Output(text)
        ExecutionEvent.InputRequested -> ServerMessage.InputRequest
        is ExecutionEvent.Finished -> ServerMessage.Finished(error)
    }
