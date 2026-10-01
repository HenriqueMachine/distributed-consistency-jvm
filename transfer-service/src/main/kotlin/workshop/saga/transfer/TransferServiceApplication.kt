package workshop.saga.transfer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling
import workshop.saga.messaging.SharedMessagingConfiguration

/** transfer-service: recebe a transferência por REST e conduz a saga como orquestrador. */
@SpringBootApplication
@EnableScheduling
@Import(SharedMessagingConfiguration::class)
class TransferServiceApplication

fun main(args: Array<String>) {
    runApplication<TransferServiceApplication>(*args)
}
