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

/** Quanto esperar pela resposta do débito, e quantas vezes perguntar antes de desistir. */
data class SagaTimeouts(val debit: Duration, val maxAttempts: Int) {
    init {
        require(maxAttempts >= 1) { "maxAttempts deve ser pelo menos 1" }
    }
}

/**
 * O coração do orquestrador (slides 20, 21 e 33): dado o estado atual e um evento, decide o
 * próximo estado e quais comandos enviar. `(estado, resposta) → (novo estado, comandos)`.
 *
 * É uma função pura: não lê banco, não publica mensagem, não consulta o relógio (o `now`
 * chega como parâmetro). Quem executa a decisão é o `SagaOrchestrator`. Por isso cada
 * regra do fluxo pode ser testada em milissegundos, sem Kafka nem Postgres.
 *
 * Regras do passo 5:
 * - timeout leva a UNKNOWN, nunca a compensação automática;
 * - em UNKNOWN, reenvia o mesmo comando (mesma chave: transferId), o participante é idempotente;
 * - tentativas esgotadas levam a NEEDS_ATTENTION;
 * - só se compensa diante de um "não" explícito.
 */
class SagaStateMachine(private val timeouts: SagaTimeouts) {

    /**
     * Decide o que fazer com [event] para a [saga] da [transfer], no instante [now].
     * Devolve uma [Decision.Transition] (novo estado + comandos) ou um [Decision.Ignore].
     */
    fun decide(transfer: Transfer, saga: Saga, event: SagaEvent, now: Instant): Decision =
        when (saga.state) {
            CREATED -> when (event) {
                SagaEvent.TransferPlaced -> saga.moveTo(
                    DEBIT_PENDING,
                    reason = "transferência criada",
                    deadlineAt = now + timeouts.debit,
                    attempts = 1,
                    debitAccount(transfer),
                )
                else -> saga.ignore(event)
            }

            DEBIT_PENDING, DEBIT_UNKNOWN -> when (event) {
                is SagaEvent.AccountDebited -> saga.moveTo(
                    PIX_PENDING,
                    reason = "débito aprovado debitId=${event.debitId}",
                    command = SendPix(transfer.id.value, transfer.to, transfer.amount.cents),
                )
                is SagaEvent.DebitDeclined -> saga.moveTo(CANCELLED, reason = "débito recusado: ${event.reason}")
                SagaEvent.TimedOut -> onDebitTimeout(transfer, saga, now)
                else -> saga.ignore(event)
            }

            // ⚠ QUEBRA passo-5: o Pix não tem prazo. Se a resposta nunca vier, nenhum
            // TimedOut tira a saga daqui: ela fica em PIX_PENDING para sempre.
            PIX_PENDING -> when (event) {
                is SagaEvent.PixSettled -> saga.moveTo(COMPLETED, reason = "Pix liquidado endToEndId=${event.endToEndId}")
                is SagaEvent.PixRejected -> saga.moveTo(
                    REFUNDING,
                    reason = "Pix recusado: ${event.reason}",
                    command = RefundDebit(transfer.id.value),
                )
                else -> saga.ignore(event)
            }

            REFUNDING -> when (event) {
                is SagaEvent.DebitRefunded -> saga.moveTo(CANCELLED, reason = "estorno concluído debitId=${event.debitId}")
                else -> saga.ignore(event)
            }

            COMPLETED, CANCELLED, NEEDS_ATTENTION -> saga.ignore(event)
        }

    /** Sem resposta não quer dizer "não": pergunta de novo, com a mesma chave (slide 33). */
    private fun onDebitTimeout(transfer: Transfer, saga: Saga, now: Instant): Decision {
        val waited = "timeout ${timeouts.debit.toSeconds()}s"
        return when {
            !saga.isOverdue(now) -> Decision.Ignore("TimedOut ignorado: o prazo de ${saga.state} ainda não venceu")
            saga.attempts >= timeouts.maxAttempts -> saga.moveTo(
                NEEDS_ATTENTION,
                reason = "$waited sem resposta após ${saga.attempts} tentativas",
            )
            else -> saga.moveTo(
                DEBIT_UNKNOWN,
                reason = "$waited → não sei se debitou, reenviando com a mesma chave tentativa=${saga.attempts + 1}",
                deadlineAt = now + timeouts.debit,
                attempts = saga.attempts + 1,
                debitAccount(transfer),
            )
        }
    }

    private fun debitAccount(transfer: Transfer) =
        DebitAccount(transfer.id.value, transfer.from, transfer.amount.cents)

    /** Transição sem novo prazo: o próximo passo ainda não tem timeout. */
    private fun Saga.moveTo(to: SagaState, reason: String, command: Message? = null) =
        moveTo(to, reason, deadlineAt = null, attempts = 0, *listOfNotNull(command).toTypedArray())

    private fun Saga.moveTo(
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
