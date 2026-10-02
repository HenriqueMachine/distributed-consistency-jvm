// Serviço executável: um jar chamado app.jar, que o Dockerfile copia para a imagem.
plugins {
    id("kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
}

// build-info.properties com o git sha: vira o app_version de cada transição da saga (slide 27).
// Sem git (ex.: build dentro do Docker), a versão é "dev".
val gitSha = providers.of(GitShaValueSource::class) {}

springBoot {
    buildInfo {
        properties {
            additional.put("gitSha", gitSha)
        }
    }
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

// `./gradlew bootRun --parallel` sobe todos os serviços; cada um loga também em logs/<serviço>.log.
tasks.bootRun {
    workingDir = rootProject.projectDir
}

tasks.jar {
    enabled = false
}
