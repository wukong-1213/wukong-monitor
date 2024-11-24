package monster.monkeyking.monitoring.metric.collector

import com.sun.management.OperatingSystemMXBean
import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.lang.management.ManagementFactory

@Component
class SystemMetricCollector : MetricCollector {
    private val logger = LoggerFactory.getLogger(javaClass)
    override val name = "system"

    override suspend fun collect(): List<MetricData> {
        val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
        val rootPath = File("/")

        // CPU 관련 메트릭
        val cpuMetrics = listOf(
            MetricData(
                type = MetricType.CPU_USAGE,
                value = osBean.cpuLoad * 100,
                unit = "percentage",
                labels = mapOf("type" to "system")
            ),
            MetricData(
                type = MetricType.CPU_CORES,
                value = osBean.availableProcessors.toDouble(),
                unit = "count",
                labels = mapOf("type" to "cpu_cores")
            )
        )

        // 메모리 관련 메트릭
        val totalMemory = osBean.totalMemorySize
        val freeMemory = osBean.freeMemorySize
        val usedMemory = (totalMemory - freeMemory).coerceAtLeast(0)

        val memoryMetrics = listOf(
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

        // 디스크 관련 메트릭
        val diskMetrics = try {
            listOf(
                MetricData(
                    type = MetricType.DISK_USED,
                    value = (rootPath.totalSpace - rootPath.freeSpace).toDouble(),
                    unit = "bytes",
                    labels = mapOf("type" to "disk_used")
                ),
                MetricData(
                    type = MetricType.DISK_TOTAL,
                    value = rootPath.totalSpace.toDouble(),
                    unit = "bytes",
                    labels = mapOf("type" to "disk_total")
                )
            )
        } catch (e: Exception) {
            logger.warn("디스크 메트릭 수집 중 오류 발생: ${e.message}")
            emptyList()
        }

        // 모든 메트릭 합치기
        return cpuMetrics + memoryMetrics + diskMetrics
    }
}
