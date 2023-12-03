/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.LeaderboardEmbedBuilder;
import services.stats.StatsReader;

import static utils.Logger.LOGGER;

public class LeaderBoardCommand extends Command {

    private final StatsReader statsReader;

    public LeaderBoardCommand(StatsReader statsReader) {
        super("leaderboard");
        this.statsReader = statsReader;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var statsList = statsReader.getTopStats();
        var embed = new LeaderboardEmbedBuilder().setStats(statsList).build();
        ctx.replyEmbeds(embed);
        LOGGER.info("Fetched leaderboard");
    }
}
