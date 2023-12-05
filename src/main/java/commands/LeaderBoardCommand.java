/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import services.stats.Stats;
import services.stats.StatsReader;

import java.awt.*;
import java.util.List;

import static utils.Logger.LOGGER;
import static utils.StringUtils.leftPad;
import static utils.StringUtils.rightPad;

public class LeaderBoardCommand extends Command {

    private final StatsReader statsReader;

    public LeaderBoardCommand(StatsReader statsReader) {
        super("leaderboard");
        this.statsReader = statsReader;
    }

    public MessageEmbed buildLeaderboardEmbed(List<Stats> statsList) {
        var embed = new EmbedBuilder();

        var desc = new StringBuilder();
        desc.append("```");
        var count = 1;
        for (var stats : statsList) {
            desc.append(rightPad(count + ")", 4))
                .append(leftPad(stats.getPlayer().name(), 40))
                .append(leftPad(Float.toString(stats.getElo()), 16))
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
        var statsList = statsReader.readTopStats();
        var embed = buildLeaderboardEmbed(statsList);
        ctx.replyEmbeds(embed);
        LOGGER.info("Fetched leaderboard");
    }
}
