package workshop.saga.transfer.domain.saga

/**
 * Em que ponto da jornada a transferência está (slides 26 e 39).
 *
 * Caminho feliz: CREATED → DEBIT_PENDING → PIX_PENDING → COMPLETED.
 * Compensação:   PIX_PENDING → REFUNDING → CANCELLED (conta destino encerrada).
 * Incerteza:     DEBIT_PENDING → DEBIT_UNKNOWN (timeout: "não sei") → pergunta de novo.
 */
enum class SagaState {
    CREATED,
    DEBIT_PENDING,

    /** Venceu o prazo sem resposta: não sabemos se debitou. Nunca compensa a partir daqui. */
    DEBIT_UNKNOWN,
    PIX_PENDING,
    REFUNDING,
    COMPLETED,
    CANCELLED,

    /**
     * Tentativas esgotadas: a saga para e uma pessoa precisa olhar. Se ela resgatar a
     * mensagem da DLT pelo Mortician (passo 7), a resposta de sucesso que chegar conclui a saga.
     */
    NEEDS_ATTENTION,
}
