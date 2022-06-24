package bot.commands;

import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.Command;
import bot.services.StatsService;
import bot.dtos.GameDto;
import bot.services.GameService;
import bot.dtos.GameResultDto;
import bot.dtos.PlayerDto;
import bot.builders.senders.GameViewMessageSender;
import bot.builders.senders.GameOverMessageSender;
import bot.imagerenderers.OthelloBoardRenderer;
import bot.services.exceptions.InvalidMoveException;
import bot.services.exceptions.NotPlayingException;
import bot.services.exceptions.TurnException;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import othello.board.OthelloBoard;
import othello.board.Tile;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.logging.Logger;

public class MoveCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.move");
    private final GameService gameService;
    private final StatsService statsService;
    private final OthelloBoardRenderer boardRenderer;

    public MoveCommand(
        GameService gameService,
        StatsService statsService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("move", "Makes a move on user's current game", "move");
        this.gameService = gameService;
        this.statsService = statsService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        String move = ctx.getParam("move");
        PlayerDto player = new PlayerDto(event.getAuthor());

        try {
            GameDto game = gameService.makeMove(player, move);
            OthelloBoard board = game.getBoard();

            // check for the next potential moves and draw a board for a response
            List<Tile> moves = board.findPotentialMoves();
            BufferedImage image = boardRenderer.drawBoard(board, moves);

            // check if the game has ended
            if (moves.size() <= 0) {
                // remove game
                gameService.deleteGame(game);
                // update elo
                GameResultDto result = game.getResult();
                statsService.updateStats(result);
                // send embed response
                new GameOverMessageSender()
                    .setGame(result)
                    .addMoveMessage(result.getWinner(), move)
                    .setTag(result)
                    .setImage(image)
                    .sendMessage(channel);
            } else {
                // send updated board back to server
                gameService.updateGame(game);

                new GameViewMessageSender()
                    .setGame(game, new Tile(move.toLowerCase()))
                    .setTag(game)
                    .setImage(image)
                    .sendMessage(channel);
            }

            logger.info("Player " + player + " made move on game");
        } catch (TurnException e) {
            channel.sendMessage("It isn't your turn.").queue();
        } catch (NotPlayingException e) {
            channel.sendMessage("You're not currently in a game.").queue();
        } catch (InvalidMoveException e) {
            channel.sendMessage("Can't make a move to " + move + ".").queue();
        }
    }
}