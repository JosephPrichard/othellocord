package bot.messages.stats;

import bot.dtos.StatsDto;
import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;

public class StatsMessageBuilder implements MessageBuilder
{
    private static final int GREEN = new Color(82, 172, 85).getRGB();

    private final EmbedBuilder embedBuilder;

    public StatsMessageBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(GREEN);
    }

    public StatsMessageBuilder setStats(StatsDto stats) {
        embedBuilder.setTitle(stats.getPlayer().getName() + "'s stats")
            .addField("Rating", Float.toString(stats.getElo()), false)
            .addField("Win Rate", stats.getWinRate() + "%", false)
            .addField("Won", Integer.toString(stats.getWon()), true)
            .addField("Lost", Integer.toString(stats.getLost()), true)
            .addField("Drawn", Integer.toString(stats.getDrawn()), true);
        return this;
    }

    public StatsMessageBuilder setAuthor(User author) {
        embedBuilder.setThumbnail(author.getAvatarUrl());
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        channel.sendMessageEmbeds(embedBuilder.build()).queue();
    }
}
