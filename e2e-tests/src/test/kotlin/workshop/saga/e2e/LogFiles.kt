package workshop.saga.e2e

import java.io.File

/**
 * Lê os logs dos serviços em `logs/<serviço>.log`, como o apresentador faz com `grep`. Vale
 * para os dois modos de execução: `bootRun` e o perfil `apps` do compose montam a mesma pasta.
 */
object LogFiles {
    private val dir = File(System.getenv("LOG_DIR") ?: "../logs")

    /** Todas as linhas de log de [service] (ex.: `pix-service`). */
    fun of(service: String): List<String> {
        val file = File(dir, "$service.log")
        check(file.exists()) { "não achei ${file.absolutePath}: o ambiente está no ar?" }
        return file.readLines()
    }
}
