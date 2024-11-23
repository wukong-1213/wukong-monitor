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
        val description = buildString {
            append("🔄 ")
            if (event.previousIp != null) {
                append("${event.previousIp} → ")
            }
            append(event.newIp)
        }

        val embed = EmbedBuilder()
            .setDescription(description)
            .setColor(Color(88, 101, 242)) // Discord 블루 컬러
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
