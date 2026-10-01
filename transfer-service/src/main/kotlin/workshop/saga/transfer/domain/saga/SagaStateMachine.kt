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
import workshop.saga.transfer.domain.saga.SagaState.PIX_PENDING
import workshop.saga.transfer.domain.saga.SagaState.REFUNDING
import java.time.Duration
import java.time.Instant

/** Quanto esperar pela resposta de cada passo. */
data class SagaTimeouts(val debit: Duration)

/**
 * O coração do orquestrador (slides 20 e 21): dado o estado atual e um evento, decide o
 * próximo estado e quais comandos enviar. `(estado, resposta) → (novo estado, comandos)`.
 *
 * É uma função pura: não lê banco, não publica mensagem, não consulta o relógio (o `now`
 * chega como parâmetro). Quem executa a decisão é o `SagaOrchestrator`. Por isso cada
 * regra do fluxo pode ser testada em milissegundos, sem Kafka nem Postgres.
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
                    DebitAccount(transfer.id.value, transfer.from, transfer.amount.cents),
                )
                else -> saga.ignore(event)
            }

            DEBIT_PENDING -> when (event) {
                is SagaEvent.AccountDebited -> saga.moveTo(
                    PIX_PENDING,
                    reason = "débito aprovado debitId=${event.debitId}",
                    deadlineAt = null,
                    SendPix(transfer.id.value, transfer.to, transfer.amount.cents),
                )
                is SagaEvent.DebitDeclined -> saga.moveTo(CANCELLED, reason = "débito recusado: ${event.reason}")
                // ⚠ QUEBRA passo-4: trata "sem resposta" como "falhou" e compensa. Se o débito
                // foi feito e só a resposta atrasou, estornamos um débito que deu certo e
                // cancelamos uma transferência que ia passar.
                SagaEvent.TimedOut ->
                    if (saga.isOverdue(now)) {
                        saga.moveTo(
                            REFUNDING,
                            reason = "timeout ${timeouts.debit.toSeconds()}s no débito: considerado falha, estornando",
                            deadlineAt = null,
                            RefundDebit(transfer.id.value),
                        )
                    } else {
                        saga.ignore(event)
                    }
                else -> saga.ignore(event)
            }

            PIX_PENDING -> when (event) {
                is SagaEvent.PixSettled -> saga.moveTo(COMPLETED, reason = "Pix liquidado endToEndId=${event.endToEndId}")
                is SagaEvent.PixRejected -> saga.moveTo(
                    REFUNDING,
                    reason = "Pix recusado: ${event.reason}",
                    deadlineAt = null,
                    RefundDebit(transfer.id.value),
                )
                else -> saga.ignore(event)
            }

            REFUNDING -> when (event) {
                is SagaEvent.DebitRefunded -> saga.moveTo(CANCELLED, reason = "estorno concluído debitId=${event.debitId}")
                else -> saga.ignore(event)
            }

            COMPLETED, CANCELLED -> saga.ignore(event)
        }

    private fun Saga.moveTo(
        to: SagaState,
        reason: String,
        deadlineAt: Instant? = null,
        vararg commands: Message,
    ) = Decision.Transition(
        from = state,
        saga = copy(state = to, deadlineAt = deadlineAt),
        commands = commands.toList(),
        reason = reason,
    )

    private fun Saga.ignore(event: SagaEvent) =
        Decision.Ignore("${event.name} ignorado: saga já está em $state")
}
