package monster.monkeyking.monitoring.config

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JdaConfig {

    @Bean
    fun discordBot(@Value("\${discord.token}") discordToken: String) =
        JDABuilder.createDefault(discordToken)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .build()

}
