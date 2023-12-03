/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.StatsEmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import services.player.Player;
import services.stats.StatsService;

import static utils.Logger.LOGGER;

public class StatsCommand extends Command {

    private final StatsService statsService;

    public StatsCommand(StatsService statsService) {
        super("stats", "Retrieves the stats profile for a player",
            new OptionData(OptionType.USER, "player", "Player to get stats profile for", false));
        this.statsService = statsService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        var userOpt = ctx.getOptionalParam("player");
        var user = userOpt != null ? userOpt.getAsUser() : ctx.getUser();

        var player = new Player(user);
        var stats = statsService.getStats(player);

        var embed = new StatsEmbedBuilder()
            .setStats(stats)
            .setAuthor(user)
            .build();
        ctx.replyEmbeds(embed);

        LOGGER.info("Retrieved stats for " + stats.getPlayer());
    }
}
