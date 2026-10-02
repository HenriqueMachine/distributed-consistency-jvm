package workshop.saga.pix.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Configuração própria do pix-service. */
@Configuration
class PixConfig {

    /** O relógio da indisponibilidade simulada do SPI. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
