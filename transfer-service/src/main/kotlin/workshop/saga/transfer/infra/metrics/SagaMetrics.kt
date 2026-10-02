package workshop.saga.transfer.infra.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import workshop.saga.transfer.domain.saga.SagaState
import workshop.saga.transfer.infra.persistence.SagaRepository

/**
 * Métricas da saga em `/actuator/metrics` (slide 45):
 *
 * - `saga.transitions{from,to}`: quantas transições aconteceram;
 * - `saga.state{state}`: quantas sagas estão em cada estado agora. As que importam para
 *   alerta são `DEBIT_UNKNOWN` e `NEEDS_ATTENTION`: sagas paradas no dashboard.
 */
@Component
class SagaMetrics(
    private val meters: MeterRegistry,
    private val sagas: SagaRepository,
) {
    private val sagasByState = MultiGauge.builder("saga.state")
        .description("Sagas em cada estado")
        .register(meters)

    /**
     * Conta a transição só **depois do commit**: uma transição que volta no rollback
     * nunca aconteceu, e não pode aparecer no dashboard.
     */
    fun transition(from: SagaState, to: SagaState) {
        val counter = Counter.builder("saga.transitions")
            .description("Transições de estado da saga")
            .tags("from", from.name, "to", to.name)
            .register(meters)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = counter.increment()
                },
            )
        } else {
            counter.increment()
        }
    }

    /** Recalcula `saga.state` a partir do banco, que é a fonte da verdade. */
    @Scheduled(fixedDelayString = "\${saga.metrics.refresh-interval:5s}")
    fun refreshStateGauges() {
        val counts = sagas.countByState()
        sagasByState.register(
            SagaState.entries.map { state ->
                MultiGauge.Row.of(Tags.of("state", state.name), counts[state] ?: 0L)
            },
            true,
        )
    }
}
