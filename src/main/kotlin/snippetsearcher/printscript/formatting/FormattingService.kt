package snippetsearcher.printscript.formatting

import org.printscript.common.Result
import org.printscript.common.Version
import org.printscript.formatter.config.FormatterConfig
import org.printscript.lexer.source.StringSourceReader
import org.printscript.runner.FormatRunner
import org.springframework.stereotype.Service
import snippetsearcher.printscript.common.toSourceError

@Service
class FormattingService {
    // Corta en el primer error, como la CLI de la librería
    fun format(
        code: String,
        version: Version,
        config: FormatterConfig,
    ): FormattingResponse {
        val formatted = StringBuilder()
        for (result in FormatRunner(config, version).format { StringSourceReader(code) }) {
            when (result) {
                is Result.Success -> formatted.append(result.value.text)
                is Result.Failure -> return FormattingResponse(formatted = null, error = result.error.toSourceError())
            }
        }
        return FormattingResponse(formatted.toString(), error = null)
    }
}
