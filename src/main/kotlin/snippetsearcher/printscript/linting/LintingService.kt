package snippetsearcher.printscript.linting

import org.printscript.analyzer.config.AnalyzerConfig
import org.printscript.common.Version
import org.printscript.lexer.source.StringSourceReader
import org.printscript.runner.AnalyzeRunner
import org.springframework.stereotype.Service
import snippetsearcher.printscript.common.handlingDeepNesting

@Service
class LintingService {
    // A diferencia del formateo, no corta en el primer error: la librería sigue con la sentencia siguiente
    fun lint(
        code: String,
        version: Version,
        config: AnalyzerConfig,
    ): LintingResponse {
        val violations = mutableListOf<LintViolation>()
        val error =
            handlingDeepNesting({ it }) {
                AnalyzeRunner(config, version).analyze({ StringSourceReader(code) }) {
                    violations.add(it.toLintViolation())
                }
                null
            }
        return LintingResponse(violations, error)
    }
}
