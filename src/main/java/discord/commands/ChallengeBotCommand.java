package discord.commands;

import discord.message.senders.GameStartMessageSender;
import discord.commands.abstracts.Command;
import discord.commands.abstracts.CommandContext;
import modules.game.Game;
import modules.Player;
import discord.renderers.OthelloBoardRenderer;
import modules.game.GameService;
import modules.game.exceptions.AlreadyPlayingException;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import utils.NumberUtils;
import utils.BotUtils;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class ChallengeBotCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.challenge");
    private final GameService gameService;
    private final OthelloBoardRenderer boardRenderer;

    public ChallengeBotCommand(GameService gameService, OthelloBoardRenderer boardRenderer) {
        super("challengebot", "Challenges PandaOthello Bot between levels 1 and 5 to an Othello game.", 0, "level");
        this.gameService = gameService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        // retrieve depth parameter and perform type validation
        Integer level = 5;
        String levelStr = ctx.getParam("level");
        if (levelStr != null) {
            level = NumberUtils.parseIntOrNull(levelStr);
            if (level == null) {
                channel.sendMessage("Level must be a number.").queue();
                return;
            }
        }

        if (!BotUtils.isValidLevel(level)) {
            channel.sendMessage("Invalid level. Type !help analyze for valid levels.").queue();
            return;
        }

        Player player = new Player(event.getAuthor());

        try {
            Game game = gameService.createBotGame(player, level);
            BufferedImage image = boardRenderer.drawBoard(game.getBoard());

            new GameStartMessageSender()
                .setGame(game)
                .setImage(image)
                .sendMessage(channel);
        } catch(AlreadyPlayingException ex) {
            channel.sendMessage("You're already in a game").queue();
        }

        logger.info("Player " + player + " challenged the bot");
    }
}

