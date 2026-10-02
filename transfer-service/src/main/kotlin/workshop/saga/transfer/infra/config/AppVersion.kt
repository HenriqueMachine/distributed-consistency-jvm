package workshop.saga.transfer.infra.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component

/**
 * A versão do código que está decidindo: o git sha gravado no `build-info.properties`
 * pelo Gradle. Vai em cada linha de `saga_transitions` (slide 26).
 */
@Component
class AppVersion(buildProperties: ObjectProvider<BuildProperties>) {
    /** O git sha curto, ou `dev` sem git. */
    val value: String = buildProperties.ifAvailable?.get("gitSha") ?: "dev"
}
