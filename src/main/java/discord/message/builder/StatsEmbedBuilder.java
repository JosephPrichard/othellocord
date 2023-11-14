/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message.builder;

import services.stats.Stats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;

public class StatsEmbedBuilder
{
    private final EmbedBuilder embedBuilder;

    public StatsEmbedBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN);
    }

    public StatsEmbedBuilder setStats(Stats stats) {
        embedBuilder.setTitle(stats.getPlayer().getName() + "'s stats")
            .addField("Rating", Float.toString(stats.getElo()), false)
            .addField("Win Rate", stats.getWinRate() + "%", false)
            .addField("Won", Integer.toString(stats.getWon()), true)
            .addField("Lost", Integer.toString(stats.getLost()), true)
            .addField("Drawn", Integer.toString(stats.getDrawn()), true);
        return this;
    }

    public StatsEmbedBuilder setAuthor(User author) {
        embedBuilder.setThumbnail(author.getAvatarUrl());
        return this;
    }

    public MessageEmbed build() {
        return embedBuilder.build();
    }
}
