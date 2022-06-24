package bot.commands;

import bot.builders.senders.GameStartMessageSender;
import bot.commands.abstracts.Command;
import bot.commands.abstracts.CommandContext;
import bot.dtos.GameDto;
import bot.dtos.PlayerDto;
import bot.imagerenderers.OthelloBoardRenderer;
import bot.services.GameService;
import bot.services.exceptions.AlreadyPlayingException;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class ChallengeBotCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.challenge");
    private final GameService gameService;
    private final OthelloBoardRenderer boardRenderer;

    public ChallengeBotCommand(GameService gameService, OthelloBoardRenderer boardRenderer) {
        super("challengebot", "Challenges PandaOthello Bot to an Othello game");
        this.gameService = gameService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        PlayerDto player = new PlayerDto(event.getAuthor());

        try {
            GameDto game = gameService.createBotGame(player);
            BufferedImage image = boardRenderer.drawBoard(game.getBoard());

            new GameStartMessageSender()
                .setGame(game)
                .setTag(game)
                .setImage(image)
                .sendMessage(channel);
        } catch(AlreadyPlayingException ex) {
            channel.sendMessage("You're already in a game").queue();
        }

        logger.info("Player " + player + " challenged the bot");
    }
}

