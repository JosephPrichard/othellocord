/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import services.stats.IStatsService;
import services.stats.Stats;

import java.awt.*;
import java.util.List;

import static commands.messaging.StringFormat.leftPad;
import static commands.messaging.StringFormat.rightPad;
import static utils.Logger.LOGGER;

public class LeaderBoardCommand extends Command {

    private final IStatsService statsService;

    public LeaderBoardCommand(IStatsService statsService) {
        this.statsService = statsService;
    }

    public MessageEmbed buildLeaderboardEmbed(List<Stats> statsList) {
        var embed = new EmbedBuilder();

        var desc = new StringBuilder();
        desc.append("```");
        var count = 1;
        for (var stats : statsList) {
            desc.append(rightPad(count + ")", 4))
                .append(leftPad(stats.getPlayer().name(), 32))
                .append(leftPad(String.format("%.2f", stats.getElo()), 12))
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
