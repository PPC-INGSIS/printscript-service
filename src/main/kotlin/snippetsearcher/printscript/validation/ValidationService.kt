package snippetsearcher.printscript.validation

import org.printscript.common.PrintScriptError
import org.printscript.common.Version
import org.printscript.lexer.source.StreamSourceReader
import org.printscript.runner.ValidateRunner
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class ValidationService {
    fun validate(
        versionId: String,
        source: InputStream,
    ): List<ValidationError> {
        val version = Version.of(versionId) ?: throw UnsupportedVersionException(versionId)

        // ValidateRunner abre la fuente una sola vez, así que el stream se puede leer directo
        return ValidateRunner(version)
            .validate { StreamSourceReader.of(source) }
            .map { it.toValidationError() }
    }
}

private fun PrintScriptError.toValidationError() = ValidationError(message, range.start.line, range.start.column)
