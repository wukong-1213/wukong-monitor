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
        val cpuCores: Int = 0,                // CPU Cores
        val systemMemoryUsed: Long = 0,       // OS Memory
        val systemMemoryTotal: Long = 0,      // OS Memory
        val diskUsed: Long = 0,               // Disk Used
        val diskTotal: Long = 0,              // Disk Total
        val publicIp: String = "unknown",
        val lastUpdated: Instant = Instant.now(),
        var initialized: Boolean = false       // 초기화 여부 추가
    )

    private data class ResourceMetrics(
        val used: Double,
        val total: Double,
        val usagePercent: Int,
        val progressBar: String,
        val unit: String = "GB"  // 기본값은 GB, 필요시 TB 등으로 변경 가능
    )

    override fun afterPropertiesSet() {
        // 이미 초기화된 경우 리턴
        if (currentMetrics.get().initialized) {
            return
        }

        discordBot.addEventListener(object : ListenerAdapter() {
            override fun onReady(event: ReadyEvent) {
                val current = currentMetrics.get()
                if (!current.initialized) {
                    logger.info("Discord 봇 준비 완료, 채널 정리 시작")
                    cleanupChannel()
                    // 초기화 완료 표시
                    currentMetrics.set(current.copy(initialized = true))
                }
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

    @EventListener
    fun handleCpuCoresMetric(event: MetricEvent.CpuCoresCollected) {
        updateMetrics { current ->
            current.copy(
                cpuCores = event.cores,
                lastUpdated = Instant.now()
            )
        }
    }

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
    fun handleDiskUsedMetric(event: MetricEvent.DiskUsedCollected) {
        updateMetrics { current ->
            current.copy(
                diskUsed = event.used,
                lastUpdated = Instant.now()
            )
        }
    }

    @EventListener
    fun handleDiskTotalMetric(event: MetricEvent.DiskTotalCollected) {
        updateMetrics { current ->
            current.copy(
                diskTotal = event.total,
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

    private fun calculateResourceMetrics(
        used: Long,
        total: Long,
        divisor: Double = 1024.0 * 1024.0 * 1024.0,  // 기본값은 GB 변환용
        unit: String = "GB"
    ): ResourceMetrics {
        val usedValue = used / divisor
        val totalValue = total / divisor
        val usagePercent = if (total > 0) {
            (used.toDouble() / total.toDouble() * 100).roundToInt()
        } else 0

        return ResourceMetrics(
            used = usedValue,
            total = totalValue,
            usagePercent = usagePercent,
            progressBar = createProgressBar(usagePercent),
            unit = unit
        )
    }

    private fun createMonitoringEmbed(): MessageEmbed {
        val metrics = currentMetrics.get()

        // Calculate metrics
        val systemMemory = calculateResourceMetrics(metrics.systemMemoryUsed, metrics.systemMemoryTotal)
        val systemDisk = calculateResourceMetrics(metrics.diskUsed, metrics.diskTotal)

        return EmbedBuilder().apply {
            setTitle("🖥️ 서버 모니터링 대시보드")
            setColor(
                getStatusColor(
                    maxOf(
                        metrics.cpuUsageSystem.roundToInt(),
                        systemMemory.usagePercent,
                        systemDisk.usagePercent
                    )
                )
            )

            // 시스템 정보
            addSystemInfoField(metrics)

            // CPU 사용량
            addCpuUsageField(metrics)

            // 메모리 사용량
            addResourceUsageField(
                "💻 시스템 메모리",
                systemMemory
            )

            // 디스크 사용량
            addResourceUsageField(
                "💾 디스크 사용량",
                systemDisk
            )

            setTimestamp(Instant.now())
        }.build()
    }

    private fun EmbedBuilder.addSystemInfoField(metrics: SystemMetrics) {
        addField(
            "🌐 시스템 정보", """
            ```
            IP: ${metrics.publicIp}
            CPU 코어: ${metrics.cpuCores}개
            ```
        """.trimIndent(), false
        )
    }

    private fun EmbedBuilder.addCpuUsageField(metrics: SystemMetrics) {
        val systemCpuBar = createProgressBar(metrics.cpuUsageSystem.roundToInt())
        addField(
            "💻 시스템 CPU", """
            ```
            $systemCpuBar ${df.format(metrics.cpuUsageSystem)}%
            ```
        """.trimIndent(), false
        )
    }

    private fun EmbedBuilder.addResourceUsageField(
        title: String,
        metrics: ResourceMetrics
    ) {
        addField(
            title, """
            ```
            ${metrics.progressBar} ${metrics.usagePercent}% (${df.format(metrics.used)}${metrics.unit}/${
                df.format(
                    metrics.total
                )
            }${metrics.unit})
            ```
        """.trimIndent(), false
        )
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
