package snippetsearcher.printscript.common

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

// Separada de Limits: estos topes son solo de la ejecución interactiva
@ConfigurationProperties("printscript.interactive")
data class InteractiveLimits(
    val inputTimeout: Duration,
    val maxDuration: Duration,
    val maxSessions: Int,
    val maxPendingInputs: Int,
    val maxMessageSize: DataSize,
    val sendTimeout: Duration,
) {
    // Se valida al bindear las properties: un valor inválido no deja arrancar el servicio
    init {
        require(inputTimeout.isPositive) { notPositive("input-timeout", inputTimeout.describe()) }
        require(maxDuration.isPositive) { notPositive("max-duration", maxDuration.describe()) }
        require(maxSessions > 0) { notPositive("max-sessions", maxSessions) }
        require(maxPendingInputs > 0) { notPositive("max-pending-inputs", maxPendingInputs) }
        // Tomcat lo recibe en milisegundos: menos de 1 ms le llegaría como 0
        require(sendTimeout.toMillis() > 0) {
            "printscript.interactive.send-timeout tiene que ser de al menos 1 ms, pero es ${sendTimeout.toMillis()} ms"
        }
        // La sesión de WebSocket recibe el tope como Int
        require(maxMessageSize.toBytes() in 1..Int.MAX_VALUE) {
            "printscript.interactive.max-message-size tiene que estar entre 1 byte y ${Int.MAX_VALUE} bytes, " +
                "pero es ${maxMessageSize.toBytes()} bytes"
        }
    }
}

private fun notPositive(
    property: String,
    value: Any,
) = "printscript.interactive.$property tiene que ser mayor a 0, pero es $value"
