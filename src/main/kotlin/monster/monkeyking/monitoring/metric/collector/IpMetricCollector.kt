package monster.monkeyking.monitoring.metric.collector

import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import monster.monkeyking.monitoring.model.response.IpResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class IpMetricCollector : NetworkMetricCollector {
    override val name = "network"

    override suspend fun collect(): List<MetricData> {
        val ipResponse = WebClient.builder().build()
            .get()
            .uri("https://api64.ipify.org?format=json")
            .retrieve()
            .awaitBody<IpResponse>()

        return listOf(
            MetricData(
                type = MetricType.PUBLIC_IP,
                value = 1.0,
                unit = "info",
                labels = mapOf("ip" to ipResponse.ip)
            )
        )
    }
}
