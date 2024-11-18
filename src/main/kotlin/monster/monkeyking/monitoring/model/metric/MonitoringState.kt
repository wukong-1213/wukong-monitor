package monster.monkeyking.monitoring.model.metric

import java.time.Instant

class MonitorState {
    private var retryAfter: Instant = Instant.EPOCH

    fun isRetrying(): Boolean =
        retryAfter.isAfter(Instant.now())

    fun setRetry() {
        retryAfter = Instant.now().plusSeconds(10)
    }

    fun clearRetry() {
        retryAfter = Instant.EPOCH
    }
}
