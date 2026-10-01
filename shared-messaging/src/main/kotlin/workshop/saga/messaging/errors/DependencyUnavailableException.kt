package workshop.saga.messaging.errors

/**
 * Uma dependência inteira está fora do ar (ex.: o SPI). Não é culpa da mensagem: ela vai
 * passar quando a dependência voltar. Por isso **não vai para a DLT**: o error handler
 * tenta de novo em intervalo fixo, sem limite, enquanto o circuit breaker decide (slide 37).
 */
open class DependencyUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
