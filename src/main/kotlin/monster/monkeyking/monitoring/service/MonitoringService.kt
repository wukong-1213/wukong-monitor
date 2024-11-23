package monster.monkeyking.monitoring.service

import monster.monkeyking.monitoring.metric.collector.MetricCollector
import monster.monkeyking.monitoring.model.event.MetricsCollectedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class MonitoringService(
    private val collectors: List<MetricCollector>,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 1000)
    suspend fun collectMetrics() {
        val metrics = collectors.flatMap { collector ->
            runCatching {
                collector.collect()
            }.onFailure { error ->
                logger.error("Error collecting metrics from ${collector.name}: ${error.message}", error)
            }.getOrElse { emptyList() }
        }

        applicationEventPublisher.publishEvent(MetricsCollectedEvent(metrics))
    }
}
