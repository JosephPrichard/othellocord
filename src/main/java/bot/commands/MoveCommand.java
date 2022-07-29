package bot.commands;

import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.Command;
import bot.dtos.AiRequestDto;
import bot.services.OthelloAiService;
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
import othello.ai.Move;
import othello.board.OthelloBoard;
import othello.board.Tile;
import othello.utils.BotUtils;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class MoveCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.move");
    private final GameService gameService;
    private final StatsService statsService;
    private final OthelloAiService aiService;
    private final OthelloBoardRenderer boardRenderer;

    public MoveCommand(
        GameService gameService,
        StatsService statsService,
        OthelloAiService aiService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("move", "Makes a move on user's current game", "move");
        this.gameService = gameService;
        this.statsService = statsService;
        this.aiService = aiService;
        this.boardRenderer = boardRenderer;
    }

    /**
     * A finish condition for "Game Over", sends an @ to either players who aren't bots that the game is complete on the given channel
     * @param channel to send message to
     * @param game included in message
     * @param move included in message
     */
    public void onGameOver(MessageChannel channel, GameDto game, String move) {
        BufferedImage image = boardRenderer.drawBoard(game.getBoard());
        // remove game from data storage
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
    }

    /**
     * A finish condition for a move that ends against a player, sends the game and move on the given channel with a tag
     * @param channel to send message to
     * @param game included in message
     * @param move included in message
     */
    public void onMoveVsPlayer(MessageChannel channel, GameDto game, String move) {
        // update game in data storage
        gameService.updateGame(game);
        // render board and send back message
        BufferedImage image = boardRenderer.drawBoardMoves(game.getBoard());
        new GameViewMessageSender()
            .setGame(game, new Tile(move.toLowerCase()))
            .setTag(game)
            .setImage(image)
            .sendMessage(channel);
    }

    /**
     * A finish condition for a move that ends against a bot, sends a message for the immediate move made by command and the move the bot will make
     * @param channel to send message to
     * @param game included in message
     */
    public void onMoveVsBot(MessageChannel channel, GameDto game) {
        // render the board and send back the message
        BufferedImage image = boardRenderer.drawBoardMoves(game.getBoard());
        new GameViewMessageSender()
            .setGame(game)
            .setImage(image)
            .sendMessage(channel);

        // queue an ai request which will find the best move, make the move, and send back a response
        int depth = BotUtils.getDepthFromId(game.getCurrentPlayer().getId());
        logger.info("Started ai make move of depth " + depth);
        aiService.findBestMove(
            new AiRequestDto<>(game.getBoard(), depth, (Move bestMove) -> {
                // make the ai's best move on the game state, and update in storage
                game.getBoard().makeMove(bestMove.getTile());
                gameService.updateGame(game);
                // check if game is over after ai makes move
                if (game.isGameOver()) {
                    onGameOver(channel, game, bestMove.getTile().toString());
                } else {
                    // render the board and send back the message
                    BufferedImage botImage = boardRenderer.drawBoardMoves(game.getBoard());
                    new GameViewMessageSender()
                        .setGame(game, bestMove.getTile())
                        .setTag(game)
                        .setImage(botImage)
                        .sendMessage(channel);
                }
            })
        );
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        String move = ctx.getParam("move");
        PlayerDto player = new PlayerDto(event.getAuthor());

        try {
            // make player's move, then respond accordingly depending on the new game state
            GameDto game = gameService.makeMove(player, move);
            if (!game.isGameOver()) {
                if (!game.isAgainstBot()) {
                    onMoveVsPlayer(channel, game, move);
                } else {
                    onMoveVsBot(channel, game);
                }
            } else {
                onGameOver(channel, game, move);
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