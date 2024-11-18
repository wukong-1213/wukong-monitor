package monster.monkeyking.monitoring.model.event

import java.time.Instant

sealed class MetricEvent(
    val timestamp: Instant = Instant.now(),
) {
    data class IpMetricCollected(val ip: String) : MetricEvent()

    data class CpuMetricCollected(
        val usage: Double,
    ) : MetricEvent()

    data class MemoryUsedCollected(
        val used: Long,
    ) : MetricEvent()

    data class MemoryTotalCollected(
        val total: Long,
    ) : MetricEvent()
}
