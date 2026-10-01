package workshop.saga.pix

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * pix-service: o "outro banco". Envia a transferência ao SPI e credita a chave de destino.
 *
 * No passo 1 ele sobe, cria o próprio banco e o tópico que vai consumir, e espera.
 */
@SpringBootApplication
class PixServiceApplication

fun main(args: Array<String>) {
    runApplication<PixServiceApplication>(*args)
}
