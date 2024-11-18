package monster.monkeyking.monitoring.service

import monster.monkeyking.monitoring.model.event.MetricEvent
import monster.monkeyking.monitoring.model.event.MetricsCollectedEvent
import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class MetricEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(condition = "!#event.metrics.empty")
    fun handleMetrics(event: MetricsCollectedEvent) {
        event.metrics.forEach { metric ->
            when (metric.type) {
                MetricType.PUBLIC_IP -> publishIpMetric(metric)
                MetricType.CPU_USAGE -> publishCpuMetric(metric)
                MetricType.MEMORY_USED -> publishMemoryUsedMetric(metric)
                MetricType.MEMORY_TOTAL -> publishMemoryTotalMetric(metric)
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
                usage = metric.value
            )
        )
    }

    private fun publishMemoryUsedMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.MemoryUsedCollected(
                used = metric.value.toLong()
            )
        )
    }

    private fun publishMemoryTotalMetric(metric: MetricData) {
        applicationEventPublisher.publishEvent(
            MetricEvent.MemoryTotalCollected(
                total = metric.value.toLong()
            )
        )
    }
}
