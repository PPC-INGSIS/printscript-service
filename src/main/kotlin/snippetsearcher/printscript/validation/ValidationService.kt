package snippetsearcher.printscript.validation

import org.printscript.lexer.source.StreamSourceReader
import org.printscript.runner.ValidateRunner
import org.springframework.stereotype.Service
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.resolveVersion
import snippetsearcher.printscript.common.toSourceError
import java.io.InputStream

@Service
class ValidationService {
    fun validate(
        versionId: String,
        source: InputStream,
    ): List<SourceError> {
        val version = resolveVersion(versionId)

        // ValidateRunner abre la fuente una sola vez, así que el stream se puede leer directo
        return ValidateRunner(version)
            .validate { StreamSourceReader.of(source) }
            .map { it.toSourceError() }
    }
}
