package monster.monkeyking.monitoring.metric.collector

import monster.monkeyking.monitoring.model.metric.MetricData
import monster.monkeyking.monitoring.model.metric.MetricType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Component
class IOMetricCollector : MetricCollector {
    private val logger = LoggerFactory.getLogger(javaClass)
    override val name = "io"

    private data class NetworkStats(
        val interfaceName: String,
        val bytesReceived: Long,
        val bytesSent: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class DiskStats(
        val device: String,
        val readBytes: Long,
        val writeBytes: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val lastNetworkStats = AtomicReference<NetworkStats?>(null)
    private val lastDiskStats = AtomicReference<DiskStats?>(null)

    override suspend fun collect(): List<MetricData> {
        return collectNetworkMetrics() + collectDiskIOMetrics()
    }

    private fun collectNetworkMetrics(): List<MetricData> {
        try {
            val stats = readNetworkStats()
            val prevStats = lastNetworkStats.get()
            lastNetworkStats.set(stats)

            if (prevStats != null && prevStats.interfaceName == stats.interfaceName) {
                val timeDiffSeconds = (stats.timestamp - prevStats.timestamp) / 1000.0
                if (timeDiffSeconds > 0) {
                    val receivedBytesPerSec =
                        ((stats.bytesReceived - prevStats.bytesReceived) / timeDiffSeconds).toLong()
                    val sentBytesPerSec = ((stats.bytesSent - prevStats.bytesSent) / timeDiffSeconds).toLong()

                    return listOf(
                        MetricData(
                            type = MetricType.NETWORK_IN,
                            value = receivedBytesPerSec.toDouble(),
                            unit = "bytes/s",
                            labels = mapOf(
                                "interface" to stats.interfaceName,
                                "direction" to "in"
                            )
                        ),
                        MetricData(
                            type = MetricType.NETWORK_OUT,
                            value = sentBytesPerSec.toDouble(),
                            unit = "bytes/s",
                            labels = mapOf(
                                "interface" to stats.interfaceName,
                                "direction" to "out"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("네트워크 통계 수집 중 오류 발생: ${e.message}")
        }
        return emptyList()
    }

    private fun collectDiskIOMetrics(): List<MetricData> {
        try {
            val stats = readDiskStats()
            val prevStats = lastDiskStats.get()
            lastDiskStats.set(stats)

            if (prevStats != null && prevStats.device == stats.device) {
                val timeDiffSeconds = (stats.timestamp - prevStats.timestamp) / 1000.0
                if (timeDiffSeconds > 0) {
                    val readBytesPerSec = ((stats.readBytes - prevStats.readBytes) / timeDiffSeconds).toLong()
                    val writeBytesPerSec = ((stats.writeBytes - prevStats.writeBytes) / timeDiffSeconds).toLong()

                    return listOf(
                        MetricData(
                            type = MetricType.DISK_READ,
                            value = readBytesPerSec.toDouble(),
                            unit = "bytes/s",
                            labels = mapOf(
                                "device" to stats.device,
                                "operation" to "read"
                            )
                        ),
                        MetricData(
                            type = MetricType.DISK_WRITE,
                            value = writeBytesPerSec.toDouble(),
                            unit = "bytes/s",
                            labels = mapOf(
                                "device" to stats.device,
                                "operation" to "write"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("디스크 I/O 통계 수집 중 오류 발생: ${e.message}")
        }
        return emptyList()
    }

    private fun readNetworkStats(): NetworkStats {
        val netDev = File("/proc/net/dev").readText()
        // eth0 또는 ens33 등의 주요 네트워크 인터페이스 찾기
        val mainInterface =
            netDev.lines()
                .firstOrNull { it.contains("eth0") || it.contains("ens") || it.contains("enp") }
                ?.trim()
                ?.split("\\s+".toRegex())
                ?: throw IllegalStateException("네트워크 인터페이스를 찾을 수 없습니다")

        // Interface: bytes packets errs drop fifo frame compressed multicast
        val interfaceName = mainInterface[0].replace(":", "")
        val bytesReceived = mainInterface[1].toLong()
        val bytesSent = mainInterface[9].toLong()

        return NetworkStats(
            interfaceName = interfaceName,
            bytesReceived = bytesReceived,
            bytesSent = bytesSent
        )
    }

    private fun readDiskStats(): DiskStats {
        val diskStats = File("/proc/diskstats").readText()
        // sda 또는 nvme0n1 등의 주요 디스크 찾기
        val mainDisk = diskStats.lines()
            .firstOrNull { it.contains(" sda ") || it.contains("nvme0n1") }
            ?.trim()
            ?.split("\\s+".toRegex())
            ?: throw IllegalStateException("디스크를 찾을 수 없습니다")

        // 필드 설명: https://www.kernel.org/doc/Documentation/ABI/testing/procfs-diskstats
        val device = mainDisk[2]
        val sectorsRead = mainDisk[5].toLong()
        val sectorsWritten = mainDisk[9].toLong()
        // 섹터 크기는 일반적으로 512 바이트
        val sectorSize = 512L

        return DiskStats(
            device = device,
            readBytes = sectorsRead * sectorSize,
            writeBytes = sectorsWritten * sectorSize
        )
    }
}
