package workshop.saga.account

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * account-service: dono das contas. Cadastra participantes e, a partir do passo 2, debita
 * quem envia e estorna na compensação.
 */
@SpringBootApplication
class AccountServiceApplication

fun main(args: Array<String>) {
    runApplication<AccountServiceApplication>(*args)
}
