package monster.monkeyking.monitoring.model.event

import java.time.Instant

data class IpChangeEvent(
    val previousIp: String?,
    val newIp: String,
    val timestamp: Instant = Instant.now(),
)
