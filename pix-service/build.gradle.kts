plugins {
    id("spring-service-conventions")
}

dependencies {
    implementation(project(":shared-messaging"))
    // Circuit breaker do SPI (slide 40), configurado em resilience4j.circuitbreaker.instances.spi.*
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
}
