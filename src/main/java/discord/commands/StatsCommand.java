/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import discord.JDASingleton;
import services.StatsService;
import services.Player;
import services.Stats;
import discord.message.StatsEmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.logging.Logger;

public class StatsCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.stats");
    private final StatsService statsService;

    public StatsCommand(StatsService statsService) {
        super("stats", "Retrieves the stats profile for a player", 0, "player");
        this.statsService = statsService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        var event = ctx.getEvent();
        var channel = event.getChannel();

        var user = event.getAuthor();
        var playerId = ctx.getParam("player");
        // check if player param is included
        if (playerId != null) {
            // if so, fetch player profile from discord
            user = JDASingleton.fetchUserFromDirect(playerId);
            if (user == null) {
                channel.sendMessage("Can't find a discord user with that id. Try using @ directly.").queue();
                return;
            }
        }

        var player = new Player(user);

        var stats = statsService.getStats(player);

        var embed = new StatsEmbedBuilder()
            .setStats(stats)
            .setAuthor(user)
            .build();

        channel.sendMessageEmbeds(embed).queue();

        logger.info("Retrieved stats for " + stats.getPlayer());
    }
}
