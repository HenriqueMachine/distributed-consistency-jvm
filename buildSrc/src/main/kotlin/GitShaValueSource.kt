import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * O git sha curto do commit atual, ou `"dev"` quando não há git: sem o binário (como dentro
 * do Docker), sem a pasta `.git` ou com qualquer outra falha. A versão é informativa e
 * nunca pode quebrar o build.
 */
abstract class GitShaValueSource : ValueSource<String, ValueSourceParameters.None> {

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): String =
        try {
            val output = ByteArrayOutputStream()
            exec.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                standardOutput = output
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
            output.toString().trim().ifEmpty { "dev" }
        } catch (e: Exception) {
            "dev"
        }
}
