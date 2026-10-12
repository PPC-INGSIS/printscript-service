package snippetsearcher.printscript.validation

import org.printscript.common.Version
import org.printscript.lexer.source.StreamSourceReader
import org.printscript.runner.ValidateRunner
import org.springframework.stereotype.Service
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.toSourceError
import java.io.InputStream

@Service
class ValidationService {
    // ValidateRunner abre la fuente una sola vez, así que el stream se puede leer directo
    fun validate(
        version: Version,
        source: InputStream,
    ): List<SourceError> =
        ValidateRunner(version)
            .validate { StreamSourceReader.of(source) }
            .map { it.toSourceError() }
}
