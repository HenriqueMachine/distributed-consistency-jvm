// Infraestrutura de mensageria comum aos serviços: outbox, relay e leitura.
plugins {
    id("spring-library-conventions")
}

dependencies {
    api(project(":contracts"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.slf4j:slf4j-api")
    implementation("io.micrometer:micrometer-core")
}
