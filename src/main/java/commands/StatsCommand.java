/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import services.player.Player;
import services.stats.Stats;
import services.stats.StatsReader;

import java.awt.*;

import static utils.Logger.LOGGER;

public class StatsCommand extends Command {

    private final StatsReader statsReader;

    public StatsCommand(StatsReader statsReader) {
        super("stats");
        this.statsReader = statsReader;
    }

    public MessageEmbed buildStatsEmbed(Stats stats, User author) {
        var embed = new EmbedBuilder();
        embed.setColor(Color.GREEN)
            .setTitle(stats.getPlayer().name() + "'s stats")
            .addField("Rating", Float.toString(stats.getElo()), false)
            .addField("Win Rate", stats.getWinRate() + "%", false)
            .addField("Won", Integer.toString(stats.getWon()), true)
            .addField("Lost", Integer.toString(stats.getLost()), true)
            .addField("Drawn", Integer.toString(stats.getDrawn()), true)
            .setThumbnail(author.getAvatarUrl());
        return embed.build();
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var userOpt = ctx.getParam("player");
        var user = userOpt != null ? userOpt.getAsUser() : ctx.getUser();

        var player = new Player(user);

        var stats = statsReader.readStats(player);

        var embed = buildStatsEmbed(stats, user);
        ctx.replyEmbeds(embed);

        LOGGER.info("Retrieved stats for " + stats.getPlayer());
    }
}
