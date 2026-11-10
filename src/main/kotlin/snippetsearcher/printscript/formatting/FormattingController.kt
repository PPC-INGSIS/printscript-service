package snippetsearcher.printscript.formatting

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.RulesResolution
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.badRequest
import snippetsearcher.printscript.common.okWithDeprecation
import snippetsearcher.printscript.common.resolveRules
import snippetsearcher.printscript.common.resolveVersion

@RestController
class FormattingController(
    private val formattingService: FormattingService,
) {
    // Si falta un campo del body, Spring responde 400 antes de llegar acá
    @PostMapping("/format", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun format(
        @RequestBody request: FormattingRequest,
    ): ResponseEntity<*> =
        when (val version = resolveVersion(request.version)) {
            is VersionResolution.Unsupported -> badRequest(version.message)
            is VersionResolution.Supported ->
                when (val rules = resolveRules(request.rules, version.version, FORMAT_RULE_SET)) {
                    is RulesResolution.Invalid -> badRequest(rules.message, rules.rule)
                    is RulesResolution.Supported -> {
                        val response =
                            formattingService
                                .format(request.code, version.version, rules.config)
                                .copy(ignoredRules = rules.ignoredRules)
                        okWithDeprecation("/format", response, rules.deprecatedKeys)
                    }
                }
        }
}
