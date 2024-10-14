/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import lombok.AllArgsConstructor;
import models.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

import java.awt.*;

import static utils.LogUtils.LOGGER;
import static utils.StringUtils.leftPad;
import static utils.StringUtils.rightPad;

@AllArgsConstructor
public class StatsHandler {
    private BotState state;

    public void handleLeaderboard(SlashCommandInteraction event) {
        var statsList = state.getStatsService().readTopStats();

        var embed = new EmbedBuilder();

        var desc = new StringBuilder();
        desc.append("```");
        var count = 1;
        for (var stats : statsList) {
            desc.append(rightPad(count + ")", 4))
                .append(leftPad(stats.getPlayer().getName(), 32))
                .append(leftPad(String.format("%.2f", stats.elo), 12))
                .append("\n");
            count++;
        }
        desc.append("```");

        embed.setTitle("Leaderboard")
            .setColor(Color.GREEN)
            .setDescription(desc.toString());

        event.replyEmbeds(embed.build()).queue();
        LOGGER.info("Fetched leaderboard");
    }

    public void handleStats(SlashCommandInteraction event) {
        var userOpt = event.getOption("player");
        var user = userOpt != null ? userOpt.getAsUser() : event.getUser();

        var player = new Player(user);

        var stats = state.getStatsService().readStats(player);

        var embed = new EmbedBuilder();
        embed.setColor(Color.GREEN)
            .setTitle(stats.player.getName() + "'s stats")
            .addField("Rating", Float.toString(stats.elo), false)
            .addField("Win Rate", stats.winRate() + "%", false)
            .addField("Won", Integer.toString(stats.won), true)
            .addField("Lost", Integer.toString(stats.lost), true)
            .addField("Drawn", Integer.toString(stats.drawn), true)
            .setThumbnail(user.getAvatarUrl());

        event.replyEmbeds(embed.build()).queue();

        LOGGER.info("Retrieved stats for {}", stats.player);
    }
}
