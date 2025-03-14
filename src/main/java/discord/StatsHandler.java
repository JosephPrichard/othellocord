/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import lombok.AllArgsConstructor;
import models.Player;
import models.Stats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

import java.awt.*;
import java.util.List;

import static utils.LogUtils.LOGGER;
import static utils.StringUtils.leftPad;
import static utils.StringUtils.rightPad;

@AllArgsConstructor
public class StatsHandler {
    private BotState state;

    public void handleLeaderboard(SlashCommandInteraction event) {
        List<Stats> statsList = state.getStatsService().readTopStats();

        EmbedBuilder embed = new EmbedBuilder();

        StringBuilder desc = new StringBuilder();
        desc.append("```");
        int count = 1;
        for (Stats stats : statsList) {
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
        OptionMapping userOpt = event.getOption("player");
        User user = userOpt != null ? userOpt.getAsUser() : event.getUser();

        Player player = new Player(user);

        Stats stats = state.getStatsService().readStats(player);

        EmbedBuilder embed = new EmbedBuilder();
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
