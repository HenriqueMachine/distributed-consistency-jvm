package workshop.saga.contracts

/**
 * Falhas que a apresentação sabe provocar. Chegam no header HTTP `X-Simulate` do
 * `POST /transfers` e viajam no header Kafka `simulate` de toda mensagem da transferência,
 * até o serviço que sabe simulá-las.
 */
enum class Simulation {
    /** transfer-service: publica o comando e cai antes do commit (escrita dupla, passo 2). */
    CRASH_AFTER_SEND,

    /** relay da outbox: publica a mesma linha duas vezes, como se caísse antes de marcar (passo 3). */
    DUPLICATE,
    ;

    companion object {
        /** Lê o valor do header; ausente ou em branco significa "nenhuma simulação". */
        fun parse(value: String?): Simulation? =
            value?.takeIf { it.isNotBlank() }?.let { raw ->
                entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                    ?: throw IllegalArgumentException(
                        "simulação desconhecida: $raw (use uma de ${entries.joinToString()})",
                    )
            }
    }
}
