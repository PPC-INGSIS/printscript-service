package snippetsearcher.printscript.common

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.util.unit.DataSize
import java.time.Duration

@ConfigurationProperties("printscript.limits")
data class Limits(
    val maxCodeSize: DataSize,
    val maxOutputSize: DataSize,
    val executionTimeout: Duration,
) {
    val codeTooLargeMessage: String
        get() = "El código supera el tamaño máximo de ${maxCodeSize.describe()}"

    fun rejectLargeCode(code: String): ResponseEntity<ProblemDetail>? =
        if (code.toByteArray(Charsets.UTF_8).size > maxCodeSize.toBytes()) {
            payloadTooLarge(codeTooLargeMessage)
        } else {
            null
        }
}

fun DataSize.describe(): String =
    if (this == DataSize.ofKilobytes(toKilobytes())) "${toKilobytes()} KB" else "${toBytes()} bytes"

fun Duration.describe(): String = if (toMillisPart() == 0) "${toSeconds()} s" else "${toMillis()} ms"
