package workshop.saga.account.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.account.domain.Debit
import workshop.saga.account.domain.DebitDecision
import workshop.saga.account.domain.DebitPolicy
import workshop.saga.account.infra.persistence.AccountRepository
import workshop.saga.account.infra.persistence.DebitRepository
import workshop.saga.account.infra.persistence.RefundRepository
import workshop.saga.contracts.AccountDebited
import workshop.saga.contracts.AccountReply
import workshop.saga.contracts.DebitAccount
import workshop.saga.contracts.DebitDeclined
import workshop.saga.contracts.DebitRefunded
import workshop.saga.contracts.Envelope
import workshop.saga.contracts.Money
import workshop.saga.contracts.RefundDebit
import workshop.saga.messaging.MessagePublisher
import workshop.saga.messaging.inbox.Inbox
import java.time.Duration

/**
 * Casos de uso do account-service na saga: debitar e estornar, respondendo ao orquestrador.
 *
 * Idempotente nas duas camadas do slide 30: a [Inbox] descarta a mesma mensagem entregue
 * de novo; e um débito (ou estorno) já existente devolve o resultado anterior em vez de
 * repetir o efeito.
 */
@Service
class DebitService(
    private val accounts: AccountRepository,
    private val debits: DebitRepository,
    private val refunds: RefundRepository,
    private val publisher: MessagePublisher,
    private val inbox: Inbox,
    private val failureSimulator: FailureSimulator,
) {
    /** Debita a conta de origem uma única vez, ou recusa se a regra não aprovar. */
    @Transactional
    fun debit(command: DebitAccount, request: Envelope) {
        if (!inbox.firstDelivery(request)) return

        val existing = debits.findByTransferId(command.transferId)
        if (existing != null) {
            log.info("já debitado debitId={} → devolvendo resultado anterior", existing.id)
            reply(AccountDebited(command.transferId, existing.id.toString()), request)
            return
        }

        val amount = Money(command.amountInCents)
        // lockByKey: dois débitos da mesma conta não gastam o mesmo saldo.
        val account = accounts.lockByKey(command.from)
        when (val decision = DebitPolicy.evaluate(account, command.from, amount)) {
            is DebitDecision.Approve -> {
                accounts.updateBalance(decision.remaining)
                val debit = debits.insert(command.transferId, command.from, amount)
                log.info("débito aprovado {} de {} debitId={} → AccountDebited", amount, command.from, debit.id)
                reply(AccountDebited(command.transferId, debit.id.toString()), request, failureSimulator.replyDelayFor(request))
            }
            is DebitDecision.Decline -> {
                log.info("débito recusado: {} → DebitDeclined", decision.reason)
                reply(DebitDeclined(command.transferId, decision.reason), request)
            }
        }
    }

    /** Compensação: devolve o débito à conta de origem, uma única vez. */
    @Transactional
    fun refund(command: RefundDebit, request: Envelope) {
        if (!inbox.firstDelivery(request)) return

        val debit = debits.findByTransferId(command.transferId)
        when {
            debit == null ->
                log.warn("sem débito para estornar → DebitRefunded")
            refunds.existsFor(debit.id) ->
                log.info("já estornado debitId={} → devolvendo resultado anterior", debit.id)
            else -> refundNow(debit)
        }
        reply(DebitRefunded(command.transferId, debit?.id?.toString()), request)
    }

    private fun refundNow(debit: Debit) {
        val account = checkNotNull(accounts.lockByKey(debit.from)) { "débito sem conta: ${debit.id}" }
        accounts.updateBalance(account.copy(balance = account.balance + debit.amount))
        refunds.insert(debit)
        log.info("estornado {} para {} debitId={} → DebitRefunded", debit.amount, debit.from, debit.id)
    }

    private fun reply(reply: AccountReply, request: Envelope, delay: Duration = Duration.ZERO) =
        publisher.publish(request.reply(reply), delay)

    private companion object {
        val log = LoggerFactory.getLogger(DebitService::class.java)
    }
}
