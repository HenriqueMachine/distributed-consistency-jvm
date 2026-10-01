package workshop.saga.contracts

/** Headers Kafka que acompanham toda mensagem da saga. */
object MessageHeaders {
    /** Identidade única desta mensagem (o eventId dos slides). Reenvios ganham um id novo. */
    const val MESSAGE_ID = "messageId"

    /** Nome do tipo da mensagem (ex.: `DebitAccount`), usado para desserializar. */
    const val MESSAGE_TYPE = "messageType"

    /** Falha a simular, vinda do header HTTP `X-Simulate` da transferência. Ver [Simulation]. */
    const val SIMULATE = "simulate"
}
