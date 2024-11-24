package monster.monkeyking.monitoring.model.event

import java.time.Instant

sealed class MetricEvent(
    val timestamp: Instant = Instant.now(),
) {
    data class IpMetricCollected(
        val ip: String
    ) : MetricEvent()

    data class CpuMetricCollected(
        val usage: Double,
        val labels: Map<String, String> = emptyMap()
    ) : MetricEvent()

    data class CpuCoresCollected(
        val cores: Int
    ) : MetricEvent()

    data class MemoryUsedCollected(
        val used: Long,
        val labels: Map<String, String> = emptyMap()
    ) : MetricEvent()

    data class MemoryTotalCollected(
        val total: Long,
        val labels: Map<String, String> = emptyMap()
    ) : MetricEvent()

    data class DiskUsedCollected(
        val used: Long
    ) : MetricEvent()

    data class DiskTotalCollected(
        val total: Long
    ) : MetricEvent()

    data class NetworkInCollected(
        val bytesPerSecond: Double,
        val interfaceName: String
    ) : MetricEvent()

    data class NetworkOutCollected(
        val bytesPerSecond: Double,
        val interfaceName: String
    ) : MetricEvent()

    data class DiskReadCollected(
        val bytesPerSecond: Double,
        val device: String
    ) : MetricEvent()

    data class DiskWriteCollected(
        val bytesPerSecond: Double,
        val device: String
    ) : MetricEvent()
}
