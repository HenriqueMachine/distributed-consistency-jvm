package workshop.saga.transfer.domain.saga

/**
 * Em que ponto da jornada a transferência está (slide 20).
 *
 * Caminho feliz: CREATED → DEBIT_PENDING → PIX_PENDING → COMPLETED.
 * Compensação:   PIX_PENDING → REFUNDING → CANCELLED (conta destino encerrada).
 */
enum class SagaState {
    CREATED,
    DEBIT_PENDING,
    PIX_PENDING,
    REFUNDING,
    COMPLETED,
    CANCELLED,
}
