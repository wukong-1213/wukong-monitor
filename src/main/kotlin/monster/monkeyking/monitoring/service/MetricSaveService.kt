package monster.monkeyking.monitoring.service

import monster.monkeyking.monitoring.model.event.MetricEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class MetricSaveService {

    @EventListener
    fun saveMetrics(event: MetricEvent) {
        // TODO: save metric to mongo
        // TODO: schedule cleanzing of old metrics
    }

}
