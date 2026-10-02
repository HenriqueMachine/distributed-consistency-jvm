package workshop.saga.transfer.domain.saga

import workshop.saga.contracts.DebitAccount
import workshop.saga.contracts.Message
import workshop.saga.contracts.RefundDebit
import workshop.saga.contracts.SendPix
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.saga.SagaState.CANCELLED
import workshop.saga.transfer.domain.saga.SagaState.COMPLETED
import workshop.saga.transfer.domain.saga.SagaState.CREATED
import workshop.saga.transfer.domain.saga.SagaState.DEBIT_PENDING
import workshop.saga.transfer.domain.saga.SagaState.DEBIT_UNKNOWN
import workshop.saga.transfer.domain.saga.SagaState.NEEDS_ATTENTION
import workshop.saga.transfer.domain.saga.SagaState.PIX_PENDING
import workshop.saga.transfer.domain.saga.SagaState.REFUNDING
import java.time.Duration
import java.time.Instant

/**
 * Quanto esperar pela resposta de cada passo, e quantas vezes perguntar antes de desistir.
 *
 * O prazo do Pix precisa caber o retry do consumidor (1 s + 2 s + 4 s) com folga: todo
 * timeout da saga precisa caber no lag (slide 44).
 */
data class SagaTimeouts(
    val debit: Duration,
    val pix: Duration,
    val refund: Duration,
    val maxAttempts: Int,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts deve ser pelo menos 1" }
    }
}

/**
 * O coração do orquestrador (slides 26, 27 e 39): dado o estado atual e um evento, decide o
 * próximo estado e quais comandos enviar. `(estado, resposta) → (novo estado, comandos)`.
 *
 * É uma função pura: não lê banco, não publica mensagem, não consulta o relógio (o `now`
 * chega como parâmetro). Quem executa a decisão é o `SagaOrchestrator`. Por isso cada
 * regra do fluxo pode ser testada em milissegundos, sem Kafka nem Postgres.
 *
 * Regras de falha:
 * - todo passo pendente tem prazo; timeout nunca leva a compensação automática;
 * - no timeout, reenvia o mesmo comando (mesma chave: transferId), o participante é
 *   idempotente; no débito, a saga entra em DEBIT_UNKNOWN para deixar explícito o "não sei";
 * - tentativas esgotadas levam a NEEDS_ATTENTION, inclusive na compensação
 *   (não existe compensação da compensação, slide 28);
 * - só se compensa diante de um "não" explícito;
 * - em NEEDS_ATTENTION, só uma resposta de sucesso (ex.: resgate pelo Mortician) tira a saga de lá.
 */
class SagaStateMachine(private val timeouts: SagaTimeouts) {

    /**
     * Decide o que fazer com [event] para a [saga] da [transfer], no instante [now].
     * Devolve uma [Decision.Transition] (novo estado + comandos) ou um [Decision.Ignore].
     */
    fun decide(transfer: Transfer, saga: Saga, event: SagaEvent, now: Instant): Decision =
        when (saga.state) {
            CREATED -> when (event) {
                SagaEvent.TransferPlaced ->
                    saga.startStep(DEBIT_PENDING, "transferência criada", debitAccount(transfer), timeouts.debit, now)
                else -> saga.ignore(event)
            }

            DEBIT_PENDING, DEBIT_UNKNOWN -> when (event) {
                is SagaEvent.AccountDebited ->
                    saga.startStep(PIX_PENDING, "débito aprovado debitId=${event.debitId}", sendPix(transfer), timeouts.pix, now)
                is SagaEvent.DebitDeclined -> saga.finish(CANCELLED, "débito recusado: ${event.reason}")
                SagaEvent.TimedOut ->
                    saga.retryOrGiveUp(now, timeouts.debit, retryIn = DEBIT_UNKNOWN, debitAccount(transfer), "não sei se debitou")
                else -> saga.ignore(event)
            }

            PIX_PENDING -> when (event) {
                is SagaEvent.PixSettled -> saga.finish(COMPLETED, "Pix liquidado endToEndId=${event.endToEndId}")
                is SagaEvent.PixRejected ->
                    saga.startStep(REFUNDING, "Pix recusado: ${event.reason}", RefundDebit(transfer.id.value), timeouts.refund, now)
                SagaEvent.TimedOut ->
                    saga.retryOrGiveUp(now, timeouts.pix, retryIn = PIX_PENDING, sendPix(transfer), "sem resposta do Pix")
                else -> saga.ignore(event)
            }

            REFUNDING -> when (event) {
                is SagaEvent.DebitRefunded -> saga.finish(CANCELLED, "estorno concluído debitId=${event.debitId}")
                SagaEvent.TimedOut ->
                    saga.retryOrGiveUp(now, timeouts.refund, retryIn = REFUNDING, RefundDebit(transfer.id.value), "estorno sem confirmação")
                else -> saga.ignore(event)
            }

            // Sucesso tardio (passo 7): alguém resgatou a mensagem pelo Mortician, ou a
            // dependência voltou depois que a saga desistiu. A saga termina como teria terminado.
            NEEDS_ATTENTION -> when (event) {
                is SagaEvent.PixSettled -> saga.finish(COMPLETED, "Pix liquidado depois de NEEDS_ATTENTION endToEndId=${event.endToEndId}")
                is SagaEvent.DebitRefunded -> saga.finish(CANCELLED, "estorno concluído depois de NEEDS_ATTENTION debitId=${event.debitId}")
                else -> saga.ignore(event)
            }

            COMPLETED, CANCELLED -> saga.ignore(event)
        }

    /** Sem resposta não quer dizer "não": pergunta de novo, com a mesma chave (slide 39). */
    private fun Saga.retryOrGiveUp(
        now: Instant,
        timeout: Duration,
        retryIn: SagaState,
        command: Message,
        doubt: String,
    ): Decision {
        val waited = "timeout ${timeout.toSeconds()}s"
        return when {
            !isOverdue(now) -> Decision.Ignore("TimedOut ignorado: o prazo de $state ainda não venceu")
            attempts >= timeouts.maxAttempts -> finish(NEEDS_ATTENTION, "$waited sem resposta após $attempts tentativas")
            else -> transition(
                to = retryIn,
                reason = "$waited → $doubt, reenviando com a mesma chave tentativa=${attempts + 1}",
                deadlineAt = now + timeout,
                attempts = attempts + 1,
                command,
            )
        }
    }

    private fun debitAccount(transfer: Transfer) = DebitAccount(transfer.id.value, transfer.from, transfer.amount.cents)

    private fun sendPix(transfer: Transfer) = SendPix(transfer.id.value, transfer.to, transfer.amount.cents)

    /** Entra num passo novo: envia o comando e começa a contar o prazo, tentativa 1. */
    private fun Saga.startStep(to: SagaState, reason: String, command: Message, timeout: Duration, now: Instant) =
        transition(to, reason, deadlineAt = now + timeout, attempts = 1, command)

    /** Estado final: sem comando, sem prazo. */
    private fun Saga.finish(to: SagaState, reason: String) =
        transition(to, reason, deadlineAt = null, attempts = 0)

    private fun Saga.transition(
        to: SagaState,
        reason: String,
        deadlineAt: Instant?,
        attempts: Int,
        vararg commands: Message,
    ) = Decision.Transition(
        from = state,
        saga = copy(state = to, deadlineAt = deadlineAt, attempts = attempts),
        commands = commands.toList(),
        reason = reason,
    )

    private fun Saga.ignore(event: SagaEvent) =
        Decision.Ignore("${event.name} ignorado: saga já está em $state")
}
