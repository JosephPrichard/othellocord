/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import services.Stats;
import discord.message.LeaderboardBuilder;
import services.StatsService;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.logging.Logger;

public class LeaderBoardCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.leaderboard");
    private final StatsService statsService;

    public LeaderBoardCommand(StatsService statsService) {
        super("leaderboard", "Retrieves the highest rated players by ELO");
        this.statsService = statsService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        var event = ctx.getEvent();
        var channel = event.getChannel();

        var statsList = statsService.getTopStats();

        var embed = new LeaderboardBuilder()
            .setStats(statsList)
            .build();

        channel.sendMessageEmbeds(embed).queue();

        logger.info("Fetched leaderboard");
    }
}
