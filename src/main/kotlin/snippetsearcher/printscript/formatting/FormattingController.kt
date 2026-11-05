package snippetsearcher.printscript.formatting

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.badRequest
import snippetsearcher.printscript.common.resolveVersion

// RFC 9745: desde cuándo están deprecadas las claves de la librería (2026-11-05)
private const val DEPRECATION = "@1793836800"

@RestController
class FormattingController(
    private val formattingService: FormattingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Si falta un campo del body, Spring responde 400 antes de llegar acá
    @PostMapping("/format", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun format(
        @RequestBody request: FormattingRequest,
    ): ResponseEntity<*> =
        when (val version = resolveVersion(request.version)) {
            is VersionResolution.Unsupported -> badRequest(version.message)
            is VersionResolution.Supported ->
                when (val rules = resolveFormatRules(request.rules, version.version)) {
                    is FormatRulesResolution.Invalid -> badRequest(rules.message, rules.rule)
                    is FormatRulesResolution.Supported -> {
                        val response =
                            formattingService
                                .format(request.code, version.version, rules.config)
                                .copy(ignoredRules = rules.ignoredRules)
                        if (rules.deprecatedKeys.isEmpty()) {
                            ResponseEntity.ok(response)
                        } else {
                            log.warn("/format recibió claves deprecadas: {}", rules.deprecatedKeys)
                            ResponseEntity.ok().header("Deprecation", DEPRECATION).body(response)
                        }
                    }
                }
        }
}
