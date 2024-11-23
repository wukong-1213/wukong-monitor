package monster.monkeyking.monitoring.service


import monster.monkeyking.monitoring.model.event.MetricEvent
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.awt.Color
import java.text.DecimalFormat
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

@Service
class ServerMonitoringDashboard(
    private val discordBot: JDA,
    @Value("\${discord.channel-id}")
    private val channelId: String
) : InitializingBean {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var dashboardMessage: Message? = null
    private val df = DecimalFormat("#.##")
    private val currentMetrics = AtomicReference(SystemMetrics())

    data class SystemMetrics(
        val cpuUsageSystem: Double = 0.0,     // OS CPU
        val systemMemoryUsed: Long = 0,       // OS Memory
        val systemMemoryTotal: Long = 0,      // OS Memory
        val publicIp: String = "unknown",
        val lastUpdated: Instant = Instant.now()
    )

    override fun afterPropertiesSet() {
        discordBot.addEventListener(object : ListenerAdapter() {
            override fun onReady(event: ReadyEvent) {
                logger.info("Discord 봇 준비 완료, 채널 정리 시작")
                cleanupChannel()
            }
        })
    }

    private fun cleanupChannel() {
        try {
            val channel = discordBot.getTextChannelById(channelId) ?: run {
                logger.error("채널을 찾을 수 없습니다: $channelId")
                return
            }

            logger.info("채널 '${channel.name}' 메시지 정리 시작...")
            var deletedCount = 0

            var messages: List<Message>
            do {
                messages = channel.history.retrievePast(100).complete()
                if (messages.isNotEmpty()) {
                    if (messages.size == 1) {
                        messages.first().delete().complete()
                        deletedCount += 1
                    } else {
                        channel.purgeMessages(messages)
                        deletedCount += messages.size
                    }
                }
            } while (messages.size == 100)

            logger.info("채널 메시지 정리 완료. 총 ${deletedCount}개의 메시지 삭제됨")
        } catch (e: Exception) {
            logger.error("채널 메시지 정리 중 오류 발생", e)
        }
    }

    @EventListener
    fun handleCpuMetric(event: MetricEvent.CpuMetricCollected) {
        updateMetrics { current ->
            when (event.labels["type"]) {
                "system" -> current.copy(
                    cpuUsageSystem = event.usage,
                    lastUpdated = Instant.now()
                )

                else -> current
            }
        }
    }

    // 메모리 업데이트 유형을 표현하는 sealed interface 추가
    private sealed interface MemoryUpdateType {
        val value: Long
        val type: String

        data class Used(override val value: Long, override val type: String) : MemoryUpdateType
        data class Total(override val value: Long, override val type: String) : MemoryUpdateType
    }

    @EventListener
    fun handleMemoryUsedMetric(event: MetricEvent.MemoryUsedCollected) {
        handleMemoryMetric(MemoryUpdateType.Used(event.used, event.labels["type"] ?: ""))
    }

    @EventListener
    fun handleMemoryTotalMetric(event: MetricEvent.MemoryTotalCollected) {
        handleMemoryMetric(MemoryUpdateType.Total(event.total, event.labels["type"] ?: ""))
    }

    private fun handleMemoryMetric(update: MemoryUpdateType) {
        updateMetrics { current ->
            when (update) {
                is MemoryUpdateType.Used -> when (update.type) {
                    "system_used" -> current.copy(
                        systemMemoryUsed = update.value,
                        lastUpdated = Instant.now()
                    )

                    else -> current
                }

                is MemoryUpdateType.Total -> when (update.type) {
                    "system_total" -> current.copy(
                        systemMemoryTotal = update.value,
                        lastUpdated = Instant.now()
                    )

                    else -> current
                }
            }
        }
    }

    @EventListener
    fun handleIpMetric(event: MetricEvent.IpMetricCollected) {
        updateMetrics { current ->
            current.copy(
                publicIp = event.ip,
                lastUpdated = Instant.now()
            )
        }
    }

    private fun updateMetrics(update: (SystemMetrics) -> SystemMetrics) {
        while (true) {
            val current = currentMetrics.get()
            val new = update(current)
            if (currentMetrics.compareAndSet(current, new)) {
                break
            }
        }
    }

    @Scheduled(fixedRate = 1000)
    fun updateDashboard() {
        val embed = createMonitoringEmbed()

        if (dashboardMessage == null) {
            sendNewDashboardMessage(embed)
        } else {
            try {
                dashboardMessage?.editMessageEmbeds(embed)
                    ?.queue(
                        { /* 성공 시 아무것도 하지 않음 */ },
                        { error ->
                            if (error is ErrorResponseException && error.errorResponse.code == 10008) {
                                logger.info("대시보드 메시지가 삭제됨. 새로운 메시지 생성")
                                dashboardMessage = null
                                sendNewDashboardMessage(embed)
                            } else {
                                logger.error("대시보드 업데이트 중 오류 발생", error)
                            }
                        }
                    )
            } catch (e: Exception) {
                logger.error("대시보드 업데이트 중 예외 발생", e)
                dashboardMessage = null
                sendNewDashboardMessage(embed)
            }
        }
    }

    private fun sendNewDashboardMessage(embed: MessageEmbed) {
        discordBot.getTextChannelById(channelId)
            ?.sendMessageEmbeds(embed)
            ?.queue(
                { message -> dashboardMessage = message },
                { error -> logger.error("새 대시보드 메시지 전송 실패", error) }
            )
    }

    private data class MemoryMetrics(
        val usedGB: Double,
        val totalGB: Double,
        val usagePercent: Int,
        val progressBar: String
    )

    private fun calculateMemoryMetrics(used: Long, total: Long): MemoryMetrics {
        val usedGB = used / (1024.0 * 1024.0 * 1024.0)
        val totalGB = total / (1024.0 * 1024.0 * 1024.0)
        val usagePercent = if (total > 0) {
            (used.toDouble() / total.toDouble() * 100).roundToInt()
        } else 0

        return MemoryMetrics(
            usedGB = usedGB,
            totalGB = totalGB,
            usagePercent = usagePercent,
            progressBar = createProgressBar(usagePercent)
        )
    }

    private fun createMonitoringEmbed(): MessageEmbed {
        val metrics = currentMetrics.get()

        // Calculate memory metrics
        val systemMemory = calculateMemoryMetrics(metrics.systemMemoryUsed, metrics.systemMemoryTotal)

        return EmbedBuilder().apply {
            setTitle("🖥️ 서버 모니터링 대시보드")
            setColor(getStatusColor(maxOf(metrics.cpuUsageSystem.roundToInt(), systemMemory.usagePercent)))

            // 공인 IP 정보
            addField("🌐 공인 IP", "```${metrics.publicIp}```", false)

            val systemCpuBar = createProgressBar(metrics.cpuUsageSystem.roundToInt())
            addField(
                "💻 시스템 CPU", """
                ```
                $systemCpuBar ${df.format(metrics.cpuUsageSystem)}%
                ```
            """.trimIndent(), false
            )

            addField(
                "💻 시스템 메모리", """
                ```
                ${systemMemory.progressBar} ${systemMemory.usagePercent}% (${df.format(systemMemory.usedGB)}GB/${
                    df.format(
                        systemMemory.totalGB
                    )
                }GB)
                ```
            """.trimIndent(), false
            )

            setTimestamp(Instant.now())
        }.build()
    }

    private fun createProgressBar(percent: Int): String {
        val filledBlocks = (percent / 5).coerceIn(0, 20)
        val emptyBlocks = 20 - filledBlocks
        return "[${"█".repeat(filledBlocks)}${" ".repeat(emptyBlocks)}]"
    }

    private fun getStatusColor(value: Int): Color {
        return when {
            value < 60 -> Color(87, 242, 135)  // 녹색 (정상)
            value < 80 -> Color(255, 255, 0)   // 노란색 (주의)
            else -> Color(255, 0, 0)           // 빨간색 (경고)
        }
    }
}
