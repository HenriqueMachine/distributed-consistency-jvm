package workshop.saga.transfer.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.AccountReply
import workshop.saga.contracts.Cid
import workshop.saga.contracts.DebitAccount
import workshop.saga.contracts.Envelope
import workshop.saga.contracts.Message
import workshop.saga.contracts.MessageCodec
import workshop.saga.contracts.PixReply
import workshop.saga.contracts.RefundDebit
import workshop.saga.contracts.SendPix
import workshop.saga.messaging.MessagePublisher
import workshop.saga.messaging.inbox.Inbox
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.Decision
import workshop.saga.transfer.domain.saga.Saga
import workshop.saga.transfer.domain.saga.SagaEvent
import workshop.saga.transfer.domain.saga.SagaStateMachine
import workshop.saga.transfer.domain.saga.SagaTransition
import workshop.saga.transfer.infra.config.AppVersion
import workshop.saga.transfer.infra.metrics.SagaMetrics
import workshop.saga.transfer.infra.persistence.SagaRepository
import workshop.saga.transfer.infra.persistence.SagaTransitionRepository
import workshop.saga.transfer.infra.persistence.TransferRepository
import java.time.Clock
import java.util.UUID

/**
 * Executa as decisões da [SagaStateMachine] (slide 22): carrega a saga, pede a decisão,
 * grava o novo estado, registra a transição e envia os comandos. Toda a regra de "o que
 * fazer" está na máquina de estados; aqui fica só o "como fazer".
 *
 * Cada comando enviado ganha um cid filho da raiz da transferência (slide 43): o reenvio do
 * débito é `TRF-1042.DEB-b7`, irmão da primeira tentativa `TRF-1042.DEB-a1`.
 */
@Service
class SagaOrchestrator(
    private val transfers: TransferRepository,
    private val sagas: SagaRepository,
    private val transitions: SagaTransitionRepository,
    private val stateMachine: SagaStateMachine,
    private val publisher: MessagePublisher,
    private val inbox: Inbox,
    private val metrics: SagaMetrics,
    private val appVersion: AppVersion,
    private val clock: Clock,
) {
    /** Começa a saga de uma transferência recém-gravada. Roda na transação de quem a criou. */
    @Transactional
    fun start(transfer: Transfer): Saga {
        val saga = Saga.start(transfer.id)
        sagas.insert(saga)
        return execute(transfer, saga, SagaEvent.TransferPlaced, Cause.of(transfer.id))
    }

    /** Aplica a resposta de um participante. Uma resposta entregue de novo é ignorada. */
    @Transactional
    fun onReply(reply: Envelope, event: SagaEvent) {
        if (!inbox.firstDelivery(reply)) return
        val transferId = TransferId(reply.transferId)
        handle(transferId, event, Cause(reply.messageId, reply.cid ?: Cid.root(transferId.value)))
    }

    /** Aplica o vencimento do prazo do passo atual. */
    @Transactional
    fun onTimeout(transferId: TransferId) = handle(transferId, SagaEvent.TimedOut, Cause.of(transferId))

    /** Sagas cujo prazo venceu; o [workshop.saga.transfer.infra.scheduling.SagaTimeoutScanner] as visita. */
    @Transactional(readOnly = true)
    fun overdue(limit: Int): List<TransferId> = sagas.findOverdue(clock.instant(), limit)

    private fun handle(transferId: TransferId, event: SagaEvent, cause: Cause) {
        // lockByTransferId: resposta e timeout da mesma transferência nunca decidem ao mesmo tempo.
        val saga = sagas.lockByTransferId(transferId)
        if (saga == null) {
            log.warn("transferência não existe: {} ignorado", event.name)
            return
        }
        val transfer = transfers.findById(transferId) ?: error("saga sem transferência: $transferId")
        execute(transfer, saga, event, cause)
    }

    private fun execute(transfer: Transfer, saga: Saga, event: SagaEvent, cause: Cause): Saga =
        when (val decision = stateMachine.decide(transfer, saga, event, clock.instant())) {
            is Decision.Transition -> {
                sagas.update(decision.saga)
                transitions.append(decision.toTransition(cause))
                val root = Cid.root(transfer.id.value)
                decision.commands.forEach {
                    publisher.publish(Envelope.of(it, transfer.simulation, root.child(segmentOf(it))))
                }
                metrics.transition(decision.from, decision.saga.state)
                // Toda transição vira log, com motivo, tentativa e o evento que a causou (slide 42).
                log.info(
                    "saga {} → {} cmd={} tentativa={} eventId={} motivo=\"{}\"",
                    decision.from, decision.saga.state,
                    decision.commands.joinToString { MessageCodec.typeOf(it) }.ifEmpty { "-" },
                    decision.saga.attempts.takeIf { it > 0 } ?: "-",
                    cause.eventId?.toString()?.take(4) ?: "-",
                    decision.reason,
                )
                decision.saga
            }
            is Decision.Ignore -> {
                log.info("{}", decision.reason)
                saga
            }
        }

    private fun Decision.Transition.toTransition(cause: Cause) = SagaTransition(
        transferId = saga.transferId,
        from = from,
        to = saga.state,
        reason = reason,
        eventId = cause.eventId,
        cid = cause.cid.value,
        appVersion = appVersion.value,
    )

    /** O segmento do cid de cada comando: `DEB`, `PIX` ou `REF`. */
    private fun segmentOf(command: Message): String = when (command) {
        is DebitAccount -> "DEB"
        is RefundDebit -> "REF"
        is SendPix -> "PIX"
        is AccountReply, is PixReply -> error("${MessageCodec.typeOf(command)} não é um comando do orquestrador")
    }

    /** O que causou uma transição: a mensagem (id e cid) ou, sem mensagem, a própria transferência. */
    private data class Cause(val eventId: UUID?, val cid: Cid) {
        companion object {
            fun of(transferId: TransferId) = Cause(eventId = null, cid = Cid.root(transferId.value))
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SagaOrchestrator::class.java)
    }
}
