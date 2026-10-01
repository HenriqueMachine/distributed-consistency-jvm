package workshop.saga.transfer.domain.saga

/**
 * Tudo o que pode acontecer com uma saga. As respostas dos participantes chegam pelo Kafka
 * e são traduzidas para estes eventos; [TimedOut] vem do relógio. Para a máquina de
 * estados, os dois são a mesma coisa: fatos que ela precisa decidir como tratar.
 */
sealed interface SagaEvent {
    /** A transferência acabou de ser gravada: a saga começa. */
    data object TransferPlaced : SagaEvent

    /** O account-service debitou (ou já tinha debitado) a conta de origem. */
    data class AccountDebited(val debitId: String) : SagaEvent

    /** O account-service recusou o débito: um "não" explícito. */
    data class DebitDeclined(val reason: String) : SagaEvent

    /** O estorno da compensação foi feito. */
    data class DebitRefunded(val debitId: String?) : SagaEvent

    /** O pix-service liquidou o Pix no SPI. */
    data class PixSettled(val endToEndId: String) : SagaEvent

    /** O pix-service recusou: dispara a compensação. */
    data class PixRejected(val reason: String) : SagaEvent

    /** O prazo do passo atual venceu sem resposta. */
    data object TimedOut : SagaEvent

    /** Nome do evento para os logs, ex.: `PixRejected`. */
    val name: String get() = this::class.simpleName ?: "SagaEvent"
}
