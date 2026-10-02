package workshop.saga.account.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import workshop.saga.contracts.SagaMessage
import workshop.saga.contracts.Simulation
import java.time.Duration

/**
 * Único lugar do account-service que sabe provocar falhas. Mantém o [DebitService] focado
 * em debitar e estornar.
 */
@Component
class FailureSimulator(
    @param:Value("\${simulation.debit-slow.delay:15s}") private val slowReplyDelay: Duration,
) {
    /**
     * `DEBIT_SLOW`: o débito é feito e gravado na hora, mas a resposta fica retida na
     * outbox. Para o orquestrador, é indistinguível de uma rede lenta (slide 38).
     */
    fun replyDelayFor(request: SagaMessage): Duration =
        if (request.simulation == Simulation.DEBIT_SLOW) {
            log.warn("simulate=DEBIT_SLOW: resposta retida por {}s", slowReplyDelay.toSeconds())
            slowReplyDelay
        } else {
            Duration.ZERO
        }

    private companion object {
        val log = LoggerFactory.getLogger(FailureSimulator::class.java)
    }
}
