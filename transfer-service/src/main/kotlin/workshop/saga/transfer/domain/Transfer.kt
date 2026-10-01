package workshop.saga.transfer.domain

import workshop.saga.contracts.Money

/** Identificador da transferência. Também é a chave de toda mensagem Kafka da saga. */
@JvmInline
value class TransferId(val value: Long) {
    override fun toString(): String = value.toString()
}

/**
 * O que o cliente pediu, ainda sem identificador: tirar [amount] da conta [from] e mandar
 * por Pix para a chave [to]. As regras de validade moram aqui.
 */
data class NewTransfer(
    val from: String,
    val to: String,
    val amount: Money,
) {
    init {
        require(from.isNotBlank()) { "from é obrigatório" }
        require(to.isNotBlank()) { "to é obrigatório" }
        require(from != to) { "from e to precisam ser chaves diferentes" }
        require(amount.cents > 0) { "amount deve ser maior que zero" }
    }
}

/** Transferência gravada. É um dado de negócio; o andamento do processo fica na [saga.Saga]. */
data class Transfer(
    val id: TransferId,
    val from: String,
    val to: String,
    val amount: Money,
)
