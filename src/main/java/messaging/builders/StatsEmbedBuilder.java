/*
 * Copyright (c) Joseph Prichard 2023.
 */

package messaging.builders;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import services.stats.Stats;

import java.awt.*;

public class StatsEmbedBuilder extends EmbedBuilder
{
    public StatsEmbedBuilder() {
       setColor(Color.GREEN);
    }

    public StatsEmbedBuilder setStats(Stats stats) {
        setTitle(stats.getPlayer().getName() + "'s stats")
            .addField("Rating", Float.toString(stats.getElo()), false)
            .addField("Win Rate", stats.getWinRate() + "%", false)
            .addField("Won", Integer.toString(stats.getWon()), true)
            .addField("Lost", Integer.toString(stats.getLost()), true)
            .addField("Drawn", Integer.toString(stats.getDrawn()), true);
        return this;
    }

    public StatsEmbedBuilder setAuthor(User author) {
        setThumbnail(author.getAvatarUrl());
        return this;
    }
}
