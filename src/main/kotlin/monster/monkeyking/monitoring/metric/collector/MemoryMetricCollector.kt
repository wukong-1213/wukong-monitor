package monster.monkeyking.monitoring.metric.collector

import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import org.springframework.stereotype.Component

@Component
class MemoryMetricCollector : SystemMetricCollector {
    override val name = "memory"

    override suspend fun collect(): List<MetricData> {
        val runtime = Runtime.getRuntime()
        return listOf(
            MetricData(
                type = MetricType.MEMORY_USED,
                value = (runtime.totalMemory() - runtime.freeMemory()).toDouble(),
                unit = "bytes",
                labels = mapOf("type" to "used")
            ),
            MetricData(
                type = MetricType.MEMORY_TOTAL,
                value = runtime.totalMemory().toDouble(),
                unit = "bytes",
                labels = mapOf("type" to "total")
            )
        )
    }
}
