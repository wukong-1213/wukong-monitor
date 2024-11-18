package monster.monkeyking.monitoring.service

import monster.monkeyking.monitoring.model.event.IpChangeEvent
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.awt.Color

@Service
class IpChangedNotifier(
    private val discordBot: JDA,
    @Value("\${discord.channel-id}")
    private val discordChannelId: String
) : InitializingBean {

    @EventListener
    fun handleIpChange(event: IpChangeEvent) {
        val embed = EmbedBuilder()
            .setTitle("🔔 IP 주소 변경 알림")
            .setColor(Color.ORANGE)
            .addField("이전 IP", event.previousIp ?: "없음", false)
            .addField("현재 IP", event.newIp, false)
            .setTimestamp(event.timestamp)
            .build()

        discordBot.getTextChannelById(discordChannelId)
            ?.sendMessageEmbeds(embed)
            ?.queue()
    }

    override fun afterPropertiesSet() {
        assert(discordBot.getTextChannelById(discordChannelId) != null) {
            "Discord 알림 채널을 찾을 수 없습니다. 채널 ID: $discordChannelId"
        }
    }
}
