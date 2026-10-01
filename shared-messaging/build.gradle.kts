// Infraestrutura de mensageria comum aos três serviços: como publicar e como ler.
plugins {
    id("spring-library-conventions")
}

dependencies {
    api(project(":contracts"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
}
