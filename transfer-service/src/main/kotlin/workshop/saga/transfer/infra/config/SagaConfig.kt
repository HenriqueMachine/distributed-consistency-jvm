package workshop.saga.transfer.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import workshop.saga.transfer.domain.saga.SagaStateMachine
import workshop.saga.transfer.domain.saga.SagaTimeouts
import java.time.Clock
import java.time.Duration

/** Prazos da saga, configuráveis em `application.yml` (`saga.timeouts.*`). */
@ConfigurationProperties("saga.timeouts")
data class SagaTimeoutProperties(
    val debit: Duration = Duration.ofSeconds(8),
    val maxAttempts: Int = 3,
)

/** Monta o domínio puro com a configuração do Spring. O domínio não conhece o Spring. */
@Configuration
@EnableConfigurationProperties(SagaTimeoutProperties::class)
class SagaConfig {

    /** A máquina de estados com os prazos configurados. */
    @Bean
    fun sagaStateMachine(timeouts: SagaTimeoutProperties) =
        SagaStateMachine(SagaTimeouts(debit = timeouts.debit, maxAttempts = timeouts.maxAttempts))

    /** O relógio da saga; nos testes do domínio, o `now` é passado direto. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
