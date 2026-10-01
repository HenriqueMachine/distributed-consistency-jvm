package workshop.saga.contracts

/**
 * Toda mensagem da saga. `transferId` é também a chave Kafka: mensagens da mesma
 * transferência caem na mesma partição e chegam em ordem (slide 7).
 *
 * As hierarquias são `sealed`: um `when` sobre elas é exaustivo, então o compilador aponta
 * qualquer mensagem nova que alguém esqueceu de tratar.
 */
sealed interface Message {
    val transferId: Long
}

// ── account.commands: orquestrador → account-service ────────────────────────────────────

/** Comandos do orquestrador para o account-service. */
sealed interface AccountCommand : Message

/** Debite [amountInCents] da conta [from]. */
data class DebitAccount(override val transferId: Long, val from: String, val amountInCents: Long) : AccountCommand

/** Compensação: devolva o débito desta transferência (slide 22). */
data class RefundDebit(override val transferId: Long) : AccountCommand

// ── account.replies: account-service → orquestrador ─────────────────────────────────────

/** Respostas do account-service ao orquestrador. */
sealed interface AccountReply : Message

/** Débito feito (agora ou antes: um reenvio recebe o mesmo [debitId]). */
data class AccountDebited(override val transferId: Long, val debitId: String) : AccountReply

/** O "não" explícito do débito: saldo insuficiente ou conta inexistente. */
data class DebitDeclined(override val transferId: Long, val reason: String) : AccountReply

/** Estorno feito. [debitId] é nulo quando não havia débito a estornar. */
data class DebitRefunded(override val transferId: Long, val debitId: String?) : AccountReply

// ── pix.commands: orquestrador → pix-service ────────────────────────────────────────────

/** Comandos do orquestrador para o pix-service. */
sealed interface PixCommand : Message

/** Envie [amountInCents] por Pix para a chave [to]. */
data class SendPix(override val transferId: Long, val to: String, val amountInCents: Long) : PixCommand

// ── pix.replies: pix-service → orquestrador ─────────────────────────────────────────────

/** Respostas do pix-service ao orquestrador. */
sealed interface PixReply : Message

/** Pix liquidado no SPI; [endToEndId] identifica a operação no sistema de pagamentos. */
data class PixSettled(override val transferId: Long, val endToEndId: String) : PixReply

/** O "não" explícito do destino (ex.: conta encerrada): dispara a compensação. */
data class PixRejected(override val transferId: Long, val reason: String) : PixReply
