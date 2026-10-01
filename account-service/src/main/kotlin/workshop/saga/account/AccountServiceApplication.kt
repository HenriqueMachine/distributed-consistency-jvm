package workshop.saga.account

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import workshop.saga.messaging.SharedMessagingConfiguration

/** account-service: dono das contas. Cadastra participantes, debita quem envia e estorna na compensação. */
@SpringBootApplication
@Import(SharedMessagingConfiguration::class)
class AccountServiceApplication

fun main(args: Array<String>) {
    runApplication<AccountServiceApplication>(*args)
}
