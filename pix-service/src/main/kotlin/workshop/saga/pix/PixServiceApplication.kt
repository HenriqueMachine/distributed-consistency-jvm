package workshop.saga.pix

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import workshop.saga.messaging.SharedMessagingConfiguration

/** pix-service: o "outro banco". Envia a transferência ao SPI e credita a chave de destino. */
@SpringBootApplication
@Import(SharedMessagingConfiguration::class)
class PixServiceApplication

fun main(args: Array<String>) {
    runApplication<PixServiceApplication>(*args)
}
