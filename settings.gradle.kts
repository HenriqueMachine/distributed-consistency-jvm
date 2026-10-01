plugins {
    // Baixa o JDK 21 automaticamente se ele não estiver instalado.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "distributed-consistency-jvm"

include(
    "contracts",
    "transfer-service",
    "account-service",
    "pix-service",
    "e2e-tests",
)
