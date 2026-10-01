package workshop.saga.messaging

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Ponto de entrada da biblioteca. Cada serviço a importa explicitamente com
 * `@Import(SharedMessagingConfiguration::class)`: nada entra no contexto por mágica.
 */
@Configuration
@ComponentScan
class SharedMessagingConfiguration
