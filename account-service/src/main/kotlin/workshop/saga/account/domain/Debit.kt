package workshop.saga.account.domain

import workshop.saga.contracts.Money

/** Identificador de débito, exibido como `d-88`. */
@JvmInline
value class DebitId(val value: Long) {
    override fun toString(): String = "d-$value"
}

/** Um débito na conta [from] por causa da transferência [transferId]. */
data class Debit(val id: DebitId, val transferId: Long, val from: String, val amount: Money)

/**
 * Um estorno: uma linha nova no extrato, e não um DELETE do débito (slide 26). As duas
 * ficam visíveis para sempre.
 */
data class Refund(val debitId: DebitId, val transferId: Long, val amount: Money)

/** Resultado da análise de um débito. */
sealed interface DebitDecision {
    /** Pode debitar; [remaining] é a conta depois do débito. */
    data class Approve(val remaining: Account) : DebitDecision

    /** Não pode debitar, pelo motivo [reason]: o "não" explícito da saga. */
    data class Decline(val reason: String) : DebitDecision
}

/** Regra do débito: só sai dinheiro que existe. Função pura, sem banco. */
object DebitPolicy {

    /** Decide se [amount] pode sair de [account] (nula = chave sem conta). */
    fun evaluate(account: Account?, from: String, amount: Money): DebitDecision = when {
        account == null -> DebitDecision.Decline("conta não encontrada chave=$from")
        account.balance < amount -> DebitDecision.Decline("saldo insuficiente: saldo ${account.balance}, débito $amount")
        else -> DebitDecision.Approve(account.copy(balance = account.balance - amount))
    }
}
