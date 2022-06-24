package bot.commands;

import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.Command;
import bot.services.GameService;
import bot.services.StatsService;
import bot.dtos.GameDto;
import bot.dtos.GameResultDto;
import bot.dtos.PlayerDto;
import bot.builders.senders.GameOverMessageSender;
import bot.imagerenderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class ForfeitCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.forfeit");
    private final GameService gameService;
    private final StatsService statsService;
    private final OthelloBoardRenderer boardRenderer;

    public ForfeitCommand(
        GameService gameService,
        StatsService statsService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("forfeit", "Forfeits the user's current game");
        this.gameService = gameService;
        this.statsService = statsService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        PlayerDto player = new PlayerDto(event.getAuthor());

        GameDto game = gameService.getGame(player);
        if (game == null) {
            channel.sendMessage("You're not currently in a game.").queue();
            return;
        }

        BufferedImage image = boardRenderer.drawBoard(game.getBoard());

        // remove game
        gameService.deleteGame(game);
        // update elo
        GameResultDto result = game.getForfeitResult();
        statsService.updateStats(result);
        // send embed response
        new GameOverMessageSender()
            .setGame(result)
            .addForfeitMessage(result.getWinner())
            .setTag(result)
            .setImage(image)
            .sendMessage(channel);

        logger.info(player + " has forfeited");
    }
}
