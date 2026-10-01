package workshop.saga.messaging

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Ponto de entrada da biblioteca. Cada serviço a importa explicitamente com
 * `@Import(SharedMessagingConfiguration::class)`: nada entra no contexto por mágica.
 *
 * `@EnableScheduling` liga o [outbox.OutboxRelay] em todos os serviços.
 */
@Configuration
@ComponentScan
@EnableScheduling
class SharedMessagingConfiguration
