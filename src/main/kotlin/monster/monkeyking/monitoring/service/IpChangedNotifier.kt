package monster.monkeyking.monitoring.service

import monster.monkeyking.monitoring.model.event.IpChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class IpChangedNotifier(
) {
    @EventListener
    fun handleIpChange(event: IpChangeEvent) {
        // TODO: discord bot
    }
}
