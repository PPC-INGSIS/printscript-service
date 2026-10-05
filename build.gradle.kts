plugins {
    // Kotlin, Spring, ktlint, detekt, cobertura y hooks
    id("ppc.kotlin-service") version "1.0.0"
}

group = "snippetsearcher"
version = "0.0.1-SNAPSHOT"

// Va acá y no en settings: el plugin ya declara mavenCentral() en el proyecto,
// y con eso Gradle ignora los repositorios de dependencias declarados en settings
repositories {
    // La librería de PrintScript del TP1. Mismas credenciales que gradle-conventions.
    maven {
        url = uri("https://maven.pkg.github.com/PPC-INGSIS/printscript")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("printscript:runner:1.1.1")
}
