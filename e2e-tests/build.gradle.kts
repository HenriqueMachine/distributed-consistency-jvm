// Cenários ponta a ponta contra o ambiente em execução (bootRun ou perfil "apps" do compose).
//   docker compose up -d && ./gradlew bootRun --parallel     (em outro terminal)
//   ./gradlew e2e
plugins {
    id("spring-library-conventions")
}

dependencies {
    testImplementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.awaitility:awaitility-kotlin")
    testImplementation("org.assertj:assertj-core")
}

// `./gradlew test` não depende do ambiente no ar; os E2E rodam só com `./gradlew e2e`.
tasks.test {
    enabled = false
}

tasks.register<Test>("e2e") {
    description = "Roda os cenários ponta a ponta contra o ambiente em execução."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed", "skipped") }
}
