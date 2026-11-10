package snippetsearcher.printscript.rules

import snippetsearcher.printscript.common.Rule

data class RulesResponse(
    val version: String,
    val rules: List<RuleInfo>,
)

data class RuleInfo(
    val id: String,
    val description: String,
    val since: String,
    val schema: Map<String, Any?>,
)

fun Rule<*, *>.toInfo() = RuleInfo(id, description, since.id, type.schema(default))
