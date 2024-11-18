package monster.monkeyking.monitoring.model.event

import monster.monkeyking.monitoring.model.metric.MetricData

data class MetricsCollectedEvent(
    val metrics: List<MetricData>,
)
