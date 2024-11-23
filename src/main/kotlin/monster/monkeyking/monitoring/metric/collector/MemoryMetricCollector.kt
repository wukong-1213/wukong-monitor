package monster.monkeyking.monitoring.metric.collector

import com.sun.management.OperatingSystemMXBean
import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory

@Component
class MemoryMetricCollector : SystemMetricCollector {
    override val name = "memory"

    override suspend fun collect(): List<MetricData> {
        val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

        // 시스템 메모리 계산
        val totalMemory = osBean.totalMemorySize
        val freeMemory = osBean.freeMemorySize

        // 실제 사용된 메모리 = 전체 메모리 - 사용 가능한 메모리
        val usedMemory = (totalMemory - freeMemory).coerceAtLeast(0)

        return listOf(
            MetricData(
                type = MetricType.MEMORY_USED,
                value = usedMemory.toDouble(),
                unit = "bytes",
                labels = mapOf("type" to "system_used")
            ),
            MetricData(
                type = MetricType.MEMORY_TOTAL,
                value = totalMemory.toDouble(),
                unit = "bytes",
                labels = mapOf("type" to "system_total")
            )
        )
    }
}
