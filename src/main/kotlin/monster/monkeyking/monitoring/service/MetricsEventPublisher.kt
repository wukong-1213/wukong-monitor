package monster.monkeyking.monitoring.service

import monster.monkeyking.monitoring.model.event.MetricEvent
import monster.monkeyking.monitoring.model.event.MetricsCollectedEvent
import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class MetricEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @EventListener(condition = "!#event.metrics.empty")
    fun handleMetrics(event: MetricsCollectedEvent) {
        event.metrics.forEach { metric ->
            when (metric.type) {
                MetricType.PUBLIC_IP -> publishIpMetric(metric)
                MetricType.CPU_USAGE -> publishCpuMetric(metric)
                MetricType.CPU_CORES -> publishCpuCoresMetric(metric)
                MetricType.MEMORY_USED -> publishMemoryUsedMetric(metric)
                MetricType.MEMORY_TOTAL -> publishMemoryTotalMetric(metric)
                MetricType.DISK_USED -> publishDiskUsedMetric(metric)
                MetricType.DISK_TOTAL -> publishDiskTotalMetric(metric)
                MetricType.DISK_READ -> publishDiskReadMetric(metric)
                MetricType.DISK_WRITE -> publishDiskWriteMetric(metric)
                MetricType.NETWORK_IN -> publishNetworkInMetric(metric)
                MetricType.NETWORK_OUT -> publishNetworkOutMetric(metric)
            }
        }
    }

    private fun publishIpMetric(metric: MetricData) {
        metric.labels["ip"]?.let { ip ->
            applicationEventPublisher.publishEvent(
                MetricEvent.IpMetricCollected(ip)
            )
        }
    }

    private fun publishCpuMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.CpuMetricCollected(
                usage = metric.value,
                labels = metric.labels
            )
        )
    }

    private fun publishCpuCoresMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.CpuCoresCollected(
                cores = metric.value.toInt()
            )
        )
    }

    private fun publishMemoryUsedMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.MemoryUsedCollected(
                used = metric.value.toLong(),
                labels = metric.labels
            )
        )
    }

    private fun publishMemoryTotalMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.MemoryTotalCollected(
                total = metric.value.toLong(),
                labels = metric.labels
            )
        )
    }

    private fun publishDiskUsedMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.DiskUsedCollected(
                used = metric.value.toLong()
            )
        )
    }

    private fun publishDiskTotalMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.DiskTotalCollected(
                total = metric.value.toLong()
            )
        )
    }

    private fun publishDiskReadMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.DiskReadCollected(
                bytesPerSecond = metric.value,
                device = metric.labels["device"] ?: "unknown"
            )
        )
    }

    private fun publishDiskWriteMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.DiskWriteCollected(
                bytesPerSecond = metric.value,
                device = metric.labels["device"] ?: "unknown"
            )
        )
    }

    private fun publishNetworkInMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.NetworkInCollected(
                bytesPerSecond = metric.value,
                interfaceName = metric.labels["interface"] ?: "unknown"
            )
        )
    }

    private fun publishNetworkOutMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.NetworkOutCollected(
                bytesPerSecond = metric.value,
                interfaceName = metric.labels["interface"] ?: "unknown"
            )
        )
    }
}
