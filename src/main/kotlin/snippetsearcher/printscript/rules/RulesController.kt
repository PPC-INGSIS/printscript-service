package snippetsearcher.printscript.rules

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.Rule
import snippetsearcher.printscript.common.RuleSet
import snippetsearcher.printscript.common.RulesResolution
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.badRequest
import snippetsearcher.printscript.common.latestVersion
import snippetsearcher.printscript.common.resolveRules
import snippetsearcher.printscript.common.resolveVersion
import snippetsearcher.printscript.formatting.FORMAT_RULES
import snippetsearcher.printscript.formatting.FORMAT_RULE_SET
import snippetsearcher.printscript.linting.LINT_RULES
import snippetsearcher.printscript.linting.LINT_RULE_SET

@RestController
@RequestMapping("/rules")
class RulesController {
    @GetMapping("/format")
    fun format(
        @RequestParam version: String?,
    ): ResponseEntity<*> = catalog(version, FORMAT_RULES)

    @GetMapping("/lint")
    fun lint(
        @RequestParam version: String?,
    ): ResponseEntity<*> = catalog(version, LINT_RULES)

    @PostMapping("/format/validate", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun validateFormat(
        @RequestBody request: RulesValidationRequest,
    ): ResponseEntity<*> = validate(request.rules, FORMAT_RULE_SET)

    @PostMapping("/lint/validate", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun validateLint(
        @RequestBody request: RulesValidationRequest,
    ): ResponseEntity<*> = validate(request.rules, LINT_RULE_SET)

    private fun catalog(
        id: String?,
        rules: List<Rule<*, *>>,
    ): ResponseEntity<*> =
        when (val version = resolveVersion(id ?: latestVersion().id)) {
            is VersionResolution.Unsupported -> badRequest(version.message)
            is VersionResolution.Supported ->
                ResponseEntity.ok(
                    RulesResponse(version.version.id, rules.filter { it.since <= version.version }.map { it.toInfo() }),
                )
        }

    // Con la última versión no se ignora ninguna regla
    private fun validate(
        rules: Map<String, Any?>,
        ruleSet: RuleSet<*>,
    ): ResponseEntity<*> =
        when (val resolution = resolveRules(rules, latestVersion(), ruleSet)) {
            is RulesResolution.Invalid -> badRequest(resolution.message, resolution.rule)
            is RulesResolution.Supported -> ResponseEntity.noContent().build<Unit>()
        }
}
