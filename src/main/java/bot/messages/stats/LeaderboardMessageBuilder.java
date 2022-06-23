package bot.messages.stats;

import bot.dtos.StatsDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.*;
import java.util.List;

public class LeaderboardMessageBuilder
{
    private static final int GREEN = new Color(82, 172, 85).getRGB();

    private final EmbedBuilder embedBuilder;

    public LeaderboardMessageBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(GREEN);
    }

    public LeaderboardMessageBuilder setStats(List<StatsDto> statsList) {
        StringBuilder desc = new StringBuilder();
        desc.append("```");
        int count = 1;
        for (StatsDto stats : statsList) {
            desc.append(count).append(": \t\t\t")
                .append(stats.getPlayer().getName())
                .append(" (").append(stats.getElo()).append(")\n");
            count++;
        }
        desc.append("```");
        embedBuilder.setTitle("Leaderboard")
            .setDescription(desc.toString());
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        channel.sendMessageEmbeds(embedBuilder.build()).queue();
    }
}
