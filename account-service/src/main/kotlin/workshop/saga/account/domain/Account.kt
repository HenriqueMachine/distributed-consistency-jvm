package workshop.saga.account.domain

import workshop.saga.contracts.Money

/** Conta de um participante, identificada pela chave Pix. */
data class Account(
    val pixKey: String,
    val name: String,
    val balance: Money,
)

/**
 * Pedido de cadastro de um participante da apresentação. As regras de validade moram aqui.
 *
 * `conta-encerrada` é reservada: é a chave que o pix-service sempre recusa, para forçar a
 * compensação.
 */
data class NewParticipant(
    val name: String,
    val pixKey: String,
    val balance: Money,
) {
    init {
        require(name.isNotBlank()) { "name é obrigatório" }
        require(PIX_KEY.matches(pixKey)) { "pixKey deve ter de 2 a 40 caracteres: letras minúsculas, números, '.', '-' ou '_'" }
        require(pixKey != RESERVED_KEY) { "a chave $RESERVED_KEY é reservada para simular conta encerrada" }
        require(balance.cents >= 0) { "balance não pode ser negativo" }
    }

    /** A conta que o cadastro abre: o nome sem espaços nas pontas. */
    fun toAccount() = Account(pixKey, name.trim(), balance)

    companion object {
        /** Chave que o pix-service sempre recusa; ninguém pode cadastrá-la. */
        const val RESERVED_KEY = "conta-encerrada"
        private val PIX_KEY = Regex("[a-z0-9._-]{2,40}")
    }
}
