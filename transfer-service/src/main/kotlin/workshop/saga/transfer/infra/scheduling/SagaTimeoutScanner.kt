package workshop.saga.transfer.infra.scheduling

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import workshop.saga.transfer.application.SagaOrchestrator

/**
 * Transforma "o prazo venceu" em um evento `SagaEvent.TimedOut`. Cada saga é tratada na
 * sua própria transação; a máquina de estados confere de novo se o prazo venceu mesmo,
 * porque a resposta pode ter chegado entre a busca e o tratamento.
 */
@Component
class SagaTimeoutScanner(private val orchestrator: SagaOrchestrator) {

    /** Uma varredura: até 50 sagas vencidas por rodada. */
    @Scheduled(fixedDelayString = "\${saga.timeouts.scan-interval:1s}")
    fun scan() {
        orchestrator.overdue(limit = 50).forEach(orchestrator::onTimeout)
    }
}
