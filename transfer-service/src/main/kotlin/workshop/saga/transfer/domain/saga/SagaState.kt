package workshop.saga.transfer.domain.saga

/**
 * Em que ponto da jornada a transferência está (slide 20).
 *
 * No passo 1 só existe o estado inicial: ninguém conversa ainda.
 */
enum class SagaState {
    CREATED,
}
