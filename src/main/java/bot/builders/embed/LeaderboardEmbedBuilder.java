package bot.builders.embed;

import bot.dtos.StatsDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.util.List;

import static bot.utils.StringUtils.*;

public class LeaderboardEmbedBuilder
{
    private final EmbedBuilder embedBuilder;

    public LeaderboardEmbedBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN);
    }

    public LeaderboardEmbedBuilder setStats(List<StatsDto> statsList) {
        StringBuilder desc = new StringBuilder();
        desc.append("```");
        int count = 1;
        for (StatsDto stats : statsList) {
            desc.append(rightPad(count + ")", 4))
                .append(leftPad(stats.getPlayer().getName(), 40))
                .append(leftPad(Float.toString(stats.getElo()), 8))
                .append("\n");
            count++;
        }
        desc.append("```");
        embedBuilder.setTitle("Leaderboard")
            .setDescription(desc.toString());
        return this;
    }

    public MessageEmbed build() {
        return embedBuilder.build();
    }
}
