package monster.monkeyking.monitoring.service

import monster.monkeyking.monitoring.model.event.IpChangeEvent
import monster.monkeyking.monitoring.model.event.MetricEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class IpChangeDetector(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var currentIp: String? = null

    @EventListener
    fun handleIpMetric(event: MetricEvent.IpMetricCollected) {
        val previousIp = currentIp
        if (previousIp == null || previousIp != event.ip) {
            logger.info("IP changed from $previousIp to ${event.ip}")
            applicationEventPublisher.publishEvent(
                IpChangeEvent(
                    previousIp = previousIp,
                    newIp = event.ip
                )
            )
        }
        currentIp = event.ip
    }
}
