package workshop.saga.mortician.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import java.time.Clock

/** Configuração própria do Mortician. */
@Configuration
class MorticianConfig {

    /**
     * O Mortician **não** manda nada para a DLT: se não conseguir guardar uma mensagem morta
     * (banco fora, por exemplo), ele tenta de novo a cada 2 s até conseguir. Com o handler
     * padrão do shared-messaging, uma falha aqui criaria um `*.DLT.DLT`, que o próprio padrão
     * `.*\.DLT` voltaria a consumir.
     */
    @Bean
    @Primary
    fun morticianErrorHandler() = DefaultErrorHandler(FixedBackOff(2_000, FixedBackOff.UNLIMITED_ATTEMPTS))

    /** O relógio dos registros de resgate. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
