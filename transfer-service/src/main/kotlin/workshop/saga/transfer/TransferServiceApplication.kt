package workshop.saga.transfer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * transfer-service: recebe a transferência por REST e conduz a saga.
 *
 * No passo 1 ele só grava a transferência; o orquestrador entra no passo 2.
 */
@SpringBootApplication
class TransferServiceApplication

fun main(args: Array<String>) {
    runApplication<TransferServiceApplication>(*args)
}
