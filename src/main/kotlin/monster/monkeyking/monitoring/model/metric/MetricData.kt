package monster.monkeyking.monitoring.model.metric

import java.time.Instant

data class MetricData(
    val type: MetricType,
    val value: Double,
    val unit: String,
    val labels: Map<String, String>,
    val timestamp: Instant = Instant.now(),
)
