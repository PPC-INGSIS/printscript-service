package snippetsearcher.printscript.linting

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
class LintingController(
    private val lintingService: LintingService,
) {
    // Si falta un campo del body, Spring responde 400 antes de llegar acá
    @PostMapping("/lint", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun lint(
        @RequestBody request: LintingRequest,
    ): ResponseEntity<*> =
        when (val version = resolveVersion(request.version)) {
            is VersionResolution.Unsupported -> badRequest(version.message)
            is VersionResolution.Supported ->
                when (val rules = resolveRules(request.rules, version.version, LINT_RULE_SET)) {
                    is RulesResolution.Invalid -> badRequest(rules.message, rules.rule)
                    is RulesResolution.Supported -> {
                        val response =
                            lintingService
                                .lint(request.code, version.version, rules.config)
                                .copy(ignoredRules = rules.ignoredRules)
                        okWithDeprecation("/lint", response, rules.deprecatedKeys)
                    }
                }
        }
}
