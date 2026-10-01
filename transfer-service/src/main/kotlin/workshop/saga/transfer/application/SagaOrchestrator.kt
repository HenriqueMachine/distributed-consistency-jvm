package workshop.saga.transfer.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.Envelope
import workshop.saga.contracts.MessageCodec
import workshop.saga.messaging.MessagePublisher
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.Decision
import workshop.saga.transfer.domain.saga.Saga
import workshop.saga.transfer.domain.saga.SagaEvent
import workshop.saga.transfer.domain.saga.SagaStateMachine
import workshop.saga.transfer.domain.saga.SagaTransition
import workshop.saga.transfer.infra.config.AppVersion
import workshop.saga.transfer.infra.persistence.SagaRepository
import workshop.saga.transfer.infra.persistence.SagaTransitionRepository
import workshop.saga.transfer.infra.persistence.TransferRepository
import java.time.Clock
import java.util.UUID

/**
 * Executa as decisões da [SagaStateMachine] (slide 21): carrega a saga, pede a decisão,
 * grava o novo estado, registra a transição e envia os comandos. Toda a regra de "o que
 * fazer" está na máquina de estados; aqui fica só o "como fazer".
 */
@Service
class SagaOrchestrator(
    private val transfers: TransferRepository,
    private val sagas: SagaRepository,
    private val transitions: SagaTransitionRepository,
    private val stateMachine: SagaStateMachine,
    private val publisher: MessagePublisher,
    private val appVersion: AppVersion,
    private val clock: Clock,
) {
    /** Começa a saga de uma transferência recém-gravada. Roda na transação de quem a criou. */
    @Transactional
    fun start(transfer: Transfer): Saga {
        val saga = Saga.start(transfer.id)
        sagas.insert(saga)
        return execute(transfer, saga, SagaEvent.TransferPlaced, eventId = null)
    }

    /** Aplica a resposta de um participante, que chegou no envelope [reply]. */
    @Transactional
    fun onReply(reply: Envelope, event: SagaEvent) {
        handle(TransferId(reply.transferId), event, reply.messageId)
    }

    /** Aplica o vencimento do prazo do passo atual. */
    @Transactional
    fun onTimeout(transferId: TransferId) = handle(transferId, SagaEvent.TimedOut, eventId = null)

    /** Sagas cujo prazo venceu; o [workshop.saga.transfer.infra.scheduling.SagaTimeoutScanner] as visita. */
    @Transactional(readOnly = true)
    fun overdue(limit: Int): List<TransferId> = sagas.findOverdue(clock.instant(), limit)

    private fun handle(transferId: TransferId, event: SagaEvent, eventId: UUID?) {
        // lockByTransferId: resposta e timeout da mesma transferência nunca decidem ao mesmo tempo.
        val saga = sagas.lockByTransferId(transferId)
        if (saga == null) {
            log.warn("transferência {} não existe: {} ignorado", transferId, event.name)
            return
        }
        val transfer = transfers.findById(transferId) ?: error("saga sem transferência: $transferId")
        execute(transfer, saga, event, eventId)
    }

    private fun execute(transfer: Transfer, saga: Saga, event: SagaEvent, eventId: UUID?): Saga =
        when (val decision = stateMachine.decide(transfer, saga, event, clock.instant())) {
            is Decision.Transition -> {
                sagas.update(decision.saga)
                transitions.append(decision.toTransition(eventId))
                decision.commands.forEach { publisher.publish(Envelope.of(it, transfer.simulation)) }
                log.info(
                    "{} {} → {} cmd={} ({})",
                    transfer.id, decision.from, decision.saga.state,
                    decision.commands.joinToString { MessageCodec.typeOf(it) }.ifEmpty { "-" },
                    decision.reason,
                )
                decision.saga
            }
            is Decision.Ignore -> {
                log.info("{} {}", transfer.id, decision.reason)
                saga
            }
        }

    private fun Decision.Transition.toTransition(eventId: UUID?) = SagaTransition(
        transferId = saga.transferId,
        from = from,
        to = saga.state,
        reason = reason,
        eventId = eventId,
        cid = SagaTransition.rootCid(saga.transferId),
        appVersion = appVersion.value,
    )

    private companion object {
        val log = LoggerFactory.getLogger(SagaOrchestrator::class.java)
    }
}
