package monster.monkeyking.monitoring.metric.collector

import com.sun.management.OperatingSystemMXBean
import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory

@Component
class CpuMetricCollector : SystemMetricCollector {
    override val name = "cpu"

    override suspend fun collect(): List<MetricData> {
        val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
        return listOf(
            MetricData(
                type = MetricType.CPU_USAGE,
                value = osBean.cpuLoad * 100,
                unit = "percentage",
                labels = mapOf("type" to "system")
            )
        )
    }
}
