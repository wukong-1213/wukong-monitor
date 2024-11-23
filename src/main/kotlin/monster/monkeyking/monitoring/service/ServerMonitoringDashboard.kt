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
        val cpuUsageProcess: Double = 0.0,
        val cpuUsageSystem: Double = 0.0,
        val memoryUsed: Long = 0,
        val memoryTotal: Long = 0,
        val publicIp: String = "unknown",
        val lastUpdated: Instant = Instant.now()
    )

    override fun afterPropertiesSet() {
        // JDA 이벤트 리스너 등록
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

            // 채널의 모든 메시지를 가져와서 삭제
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
            } while (messages.size == 100) // 100개씩 가져오므로, 100개면 더 있을 수 있음

            logger.info("채널 메시지 정리 완료. 총 ${deletedCount}개의 메시지 삭제됨")
        } catch (e: Exception) {
            logger.error("채널 메시지 정리 중 오류 발생", e)
        }
    }

    @EventListener
    fun handleCpuMetric(event: MetricEvent.CpuMetricCollected) {
        updateMetrics { current ->
            current.copy(
                cpuUsageProcess = event.usage,
                lastUpdated = Instant.now()
            )
        }
    }

    @EventListener
    fun handleMemoryUsedMetric(event: MetricEvent.MemoryUsedCollected) {
        updateMetrics { current ->
            current.copy(
                memoryUsed = event.used,
                lastUpdated = Instant.now()
            )
        }
    }

    @EventListener
    fun handleMemoryTotalMetric(event: MetricEvent.MemoryTotalCollected) {
        updateMetrics { current ->
            current.copy(
                memoryTotal = event.total,
                lastUpdated = Instant.now()
            )
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
            // 메시지가 아직 존재하는지 확인
            try {
                dashboardMessage?.editMessageEmbeds(embed)
                    ?.queue(
                        { /* 성공 시 아무것도 하지 않음 */ },
                        { error ->
                            if (error is ErrorResponseException && error.errorResponse.code == 10008) { // Unknown Message 에러 코드
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

    private fun createMonitoringEmbed(): MessageEmbed {
        val metrics = currentMetrics.get()
        val memoryUsedGB = metrics.memoryUsed / (1024.0 * 1024.0 * 1024.0)
        val memoryTotalGB = metrics.memoryTotal / (1024.0 * 1024.0 * 1024.0)
        val memoryUsagePercent = if (metrics.memoryTotal > 0) {
            (metrics.memoryUsed.toDouble() / metrics.memoryTotal.toDouble() * 100).roundToInt()
        } else 0

        return EmbedBuilder().apply {
            setTitle("🖥️ 서버 모니터링 대시보드")
            setColor(getStatusColor(maxOf(metrics.cpuUsageProcess.roundToInt(), memoryUsagePercent)))

            // 공인 IP 정보
            addField(
                "🌐 공인 IP", """
                ```
                ${metrics.publicIp}                
                ```
            """.trimIndent(), true
            )

            // CPU 사용량 프로그레스 바
            val cpuBar = createProgressBar(metrics.cpuUsageProcess.roundToInt())
            addField(
                "CPU 사용량", """
                ```
                $cpuBar ${df.format(metrics.cpuUsageProcess)}%
                ```
            """.trimIndent(), false
            )

            // 메모리 사용량 프로그레스 바
            val memoryBar = createProgressBar(memoryUsagePercent)
            addField(
                "메모리 사용량", """
                ```
                $memoryBar $memoryUsagePercent% (${df.format(memoryUsedGB)}GB/${df.format(memoryTotalGB)}GB)
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
