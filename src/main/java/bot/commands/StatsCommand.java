package bot.commands;

import bot.JDASingleton;
import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.Command;
import bot.services.StatsService;
import bot.dtos.PlayerDto;
import bot.dtos.StatsDto;
import bot.builders.embed.StatsEmbedBuilder;
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
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        User user = event.getAuthor();
        String playerId = ctx.getParam("player");
        // check if player param is included
        if (playerId != null) {
            // if so, fetch player profile from discord
            user = JDASingleton.fetchUserFromDirect(playerId);
            if (user == null) {
                channel.sendMessage("Can't find a discord user with that id. Try using @ directly.").queue();
                return;
            }
        }

        PlayerDto player = new PlayerDto(user);

        StatsDto stats = statsService.getStats(player);

        MessageEmbed embed = new StatsEmbedBuilder()
            .setStats(stats)
            .setAuthor(user)
            .build();

        channel.sendMessageEmbeds(embed).queue();

        logger.info("Retrieved stats for " + stats.getPlayer());
    }
}
