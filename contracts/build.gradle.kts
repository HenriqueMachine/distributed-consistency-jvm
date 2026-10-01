// Contratos de mensagem: Kotlin puro + Jackson, sem Spring. Todos os serviços dependem daqui.
plugins {
    id("spring-library-conventions")
}

dependencies {
    api("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
