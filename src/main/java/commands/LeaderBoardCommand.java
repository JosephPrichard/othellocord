/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import models.Stats;
import services.StatsService;

import java.awt.*;
import java.util.List;

import static utils.Log.LOGGER;
import static utils.StringFormat.leftPad;
import static utils.StringFormat.rightPad;

@AllArgsConstructor
public class LeaderBoardCommand extends CommandHandler {

    private final StatsService statsService;

    public static MessageEmbed buildLeaderboardEmbed(List<Stats> statsList) {
        var embed = new EmbedBuilder();

        var desc = new StringBuilder();
        desc.append("```");
        var count = 1;
        for (var stats : statsList) {
            desc.append(rightPad(count + ")", 4))
                .append(leftPad(stats.player().name(), 32))
                .append(leftPad(String.format("%.2f", stats.elo()), 12))
                .append("\n");
            count++;
        }
        desc.append("```");

        embed.setTitle("Leaderboard")
            .setColor(Color.GREEN)
            .setDescription(desc.toString());
        return embed.build();
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var statsList = statsService.readTopStats();
        var embed = buildLeaderboardEmbed(statsList);
        ctx.replyEmbeds(embed);
        LOGGER.info("Fetched leaderboard");
    }
}
