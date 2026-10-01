// Biblioteca compartilhada: usa as versões do Spring Boot, mas não gera jar executável.
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}
