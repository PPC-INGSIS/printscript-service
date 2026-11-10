package snippetsearcher.printscript.rules

import org.printscript.common.Version
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.Rule
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.badRequest
import snippetsearcher.printscript.common.resolveVersion
import snippetsearcher.printscript.formatting.FORMAT_RULES
import snippetsearcher.printscript.linting.LINT_RULES

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

    private fun catalog(
        id: String?,
        rules: List<Rule<*, *>>,
    ): ResponseEntity<*> =
        when (val version = resolveVersion(id ?: Version.entries.last().id)) {
            is VersionResolution.Unsupported -> badRequest(version.message)
            is VersionResolution.Supported ->
                ResponseEntity.ok(
                    RulesResponse(
                        version = version.version.id,
                        rules =
                            rules
                                .filter { it.since <= version.version }
                                .map { RuleInfo(it.id, it.description, it.since.id, it.type.schema(it.default)) },
                    ),
                )
        }
}
