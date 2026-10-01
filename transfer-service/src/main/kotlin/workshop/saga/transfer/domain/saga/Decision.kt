package workshop.saga.transfer.domain.saga

import workshop.saga.contracts.Message

/** O que a máquina de estados decidiu fazer com um evento. */
sealed interface Decision {

    /** Avançar: gravar [saga] no novo estado e enviar [commands]. [reason] vai para o log e para `saga_transitions`. */
    data class Transition(
        val from: SagaState,
        val saga: Saga,
        val commands: List<Message>,
        val reason: String,
    ) : Decision

    /** O evento não muda nada: resposta atrasada, duplicada, ou saga já encerrada. */
    data class Ignore(val reason: String) : Decision
}
