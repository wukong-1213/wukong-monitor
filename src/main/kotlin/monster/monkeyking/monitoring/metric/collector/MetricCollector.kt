package monster.monkeyking.monitoring.metric.collector

import monster.monkeyking.monitoring.model.metric.MetricData

interface MetricCollector {
    val name: String
    suspend fun collect(): List<MetricData>
}

interface SystemMetricCollector : MetricCollector
interface NetworkMetricCollector : MetricCollector
