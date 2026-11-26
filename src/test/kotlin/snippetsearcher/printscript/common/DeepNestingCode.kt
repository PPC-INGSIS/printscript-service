package snippetsearcher.printscript.common

// Con 5.000 niveles ya desborda; 20.000 deja margen por si la JVM corre con un stack más grande
private const val DEEP_NESTING = 20_000

val deeplyNestedCode = "println(${"(".repeat(DEEP_NESTING)}1${")".repeat(DEEP_NESTING)});"
