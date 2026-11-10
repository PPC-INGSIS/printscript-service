package snippetsearcher.printscript.rules

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
