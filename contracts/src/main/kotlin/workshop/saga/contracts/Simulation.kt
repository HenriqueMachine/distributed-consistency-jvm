package workshop.saga.contracts

/**
 * Falhas que a apresentação sabe provocar. Chegam no header HTTP `X-Simulate` do
 * `POST /transfers` e viajam no header Kafka `simulate` de toda mensagem da transferência,
 * até o serviço que sabe simulá-las.
 *
 * O "passo N" de cada uma é o passo em que o problema aparece; o passo seguinte o resolve.
 */
enum class Simulation {
    /**
     * transfer-service: publica o comando e cai antes do commit.
     * Escrita dupla: aparece no passo 2; a outbox do passo 3 resolve.
     */
    CRASH_AFTER_SEND,

    /**
     * relay da outbox: publica a mesma linha duas vezes, como se caísse antes de marcar.
     * Aparece no passo 3; a idempotência do passo 4 resolve.
     */
    DUPLICATE,

    /**
     * account-service: debita na hora, mas a resposta só sai 15 s depois.
     * Aparece no passo 4; o timeout = "não sei" do passo 5 resolve.
     */
    DEBIT_SLOW,

    /**
     * pix-service: o consumidor lança exceção em toda tentativa.
     * Aparece no passo 5; retry + DLT do passo 6 resolve.
     */
    PIX_CRASH,
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
