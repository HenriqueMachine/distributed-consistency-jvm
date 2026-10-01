package workshop.saga.mortician

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import workshop.saga.messaging.SharedMessagingConfiguration

/**
 * mortician-service: o dono das DLTs (slide 38). Consome todas elas, guarda cada mensagem
 * com o erro e expõe listar, investigar e republicar.
 */
@SpringBootApplication
@Import(SharedMessagingConfiguration::class)
class MorticianServiceApplication

fun main(args: Array<String>) {
    runApplication<MorticianServiceApplication>(*args)
}
