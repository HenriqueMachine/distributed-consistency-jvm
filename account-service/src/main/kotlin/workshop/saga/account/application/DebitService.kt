package workshop.saga.account.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

/** Casos de uso do account-service na saga: debitar e estornar, respondendo ao orquestrador. */
@Service
class DebitService(
    private val accounts: AccountRepository,
    private val debits: DebitRepository,
    private val refunds: RefundRepository,
    private val publisher: MessagePublisher,
) {
    /**
     * Debita a conta de origem, ou recusa se a regra não aprovar.
     *
     * ⚠ QUEBRA passo-3: a outbox entrega pelo menos uma vez, e às vezes duas. Este método
     * não pergunta se já debitou esta transferência: cada entrega repetida é um débito novo.
     */
    @Transactional
    fun debit(command: DebitAccount, request: Envelope) {
        val amount = Money(command.amountInCents)
        // lockByKey: dois débitos da mesma conta não gastam o mesmo saldo.
        val account = accounts.lockByKey(command.from)
        val reply = when (val decision = DebitPolicy.evaluate(account, command.from, amount)) {
            is DebitDecision.Approve -> {
                accounts.updateBalance(decision.remaining)
                val debit = debits.insert(command.transferId, command.from, amount)
                log.info("{} debitado {} de {} debitId={} → AccountDebited", command.transferId, amount, command.from, debit.id)
                AccountDebited(command.transferId, debit.id.toString())
            }
            is DebitDecision.Decline -> {
                log.info("{} débito recusado: {} → DebitDeclined", command.transferId, decision.reason)
                DebitDeclined(command.transferId, decision.reason)
            }
        }
        reply(reply, request)
    }

    /** Compensação: devolve o débito à conta de origem. Sem débito, não há o que estornar. */
    @Transactional
    fun refund(command: RefundDebit, request: Envelope) {
        val debit = debits.findFirstByTransferId(command.transferId)
        if (debit == null) {
            log.warn("{} sem débito para estornar → DebitRefunded", command.transferId)
        } else {
            val account = checkNotNull(accounts.lockByKey(debit.from)) { "débito sem conta: ${debit.id}" }
            accounts.updateBalance(account.copy(balance = account.balance + debit.amount))
            refunds.insert(debit)
            log.info("{} estornado {} para {} debitId={} → DebitRefunded", command.transferId, debit.amount, debit.from, debit.id)
        }
        reply(DebitRefunded(command.transferId, debit?.id?.toString()), request)
    }

    private fun reply(reply: AccountReply, request: Envelope) =
        publisher.publish(Envelope.of(reply, request.simulation))

    private companion object {
        val log = LoggerFactory.getLogger(DebitService::class.java)
    }
}
