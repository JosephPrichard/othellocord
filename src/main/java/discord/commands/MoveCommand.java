package discord.commands;

import discord.commands.abstracts.CommandContext;
import discord.commands.abstracts.Command;
import modules.ai.AiRequest;
import modules.ai.AiRequestService;
import modules.stats.StatsService;
import modules.game.Game;
import modules.game.GameService;
import modules.game.GameResult;
import modules.Player;
import discord.message.senders.GameViewMessageSender;
import discord.message.senders.GameOverMessageSender;
import discord.renderers.OthelloBoardRenderer;
import modules.game.exceptions.InvalidMoveException;
import modules.game.exceptions.NotPlayingException;
import modules.game.exceptions.TurnException;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import othello.ai.Move;
import othello.board.Tile;
import utils.BotUtils;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class MoveCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.move");
    private final GameService gameService;
    private final StatsService statsService;
    private final AiRequestService aiService;
    private final OthelloBoardRenderer boardRenderer;

    public MoveCommand(
        GameService gameService,
        StatsService statsService,
        AiRequestService aiService,
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
    public void onGameOver(MessageChannel channel, Game game, String move) {
        BufferedImage image = boardRenderer.drawBoard(game.getBoard());
        // remove game from data storage
        gameService.deleteGame(game);
        // update elo
        GameResult result = game.getResult();
        statsService.updateStats(result);
        // send embed response
        new GameOverMessageSender()
            .setGame(result)
            .addMoveMessage(result.getWinner(), move)
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
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
    public void onMoveVsPlayer(MessageChannel channel, Game game, String move) {
        // update game in data storage
        gameService.saveGame(game);
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
    public void onMoveVsBot(MessageChannel channel, Game game) {
        // render the board and send back the message
        BufferedImage image = boardRenderer.drawBoardMoves(game.getBoard());
        new GameViewMessageSender()
            .setGame(game)
            .setImage(image)
            .sendMessage(channel);

        // queue an ai request which will find the best move, make the move, and send back a response
        int depth = BotUtils.getDepthFromId(game.getCurrentPlayer().getId());
        aiService.findBestMove(
            new AiRequest<>(game.getBoard(), depth, (Move bestMove) -> {
                // make the ai's best move on the game state, and update in storage
                game.getBoard().makeMove(bestMove.getTile());
                gameService.saveGame(game);
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

        logger.info("Started ai make move of depth " + depth);
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        String move = ctx.getParam("move");
        Player player = new Player(event.getAuthor());

        try {
            // make player's move, then respond accordingly depending on the new game state
            Game game = gameService.makeMove(player, move);
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