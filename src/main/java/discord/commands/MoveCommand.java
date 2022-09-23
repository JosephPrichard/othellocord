package discord.commands;

import discord.commands.abstracts.CommandContext;
import discord.commands.abstracts.Command;
import modules.agent.AgentRequest;
import modules.agent.AgentService;
import modules.stats.StatsService;
import modules.game.Game;
import modules.game.GameService;
import modules.game.GameResult;
import modules.player.Player;
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
    private final AgentService agentService;
    private final OthelloBoardRenderer boardRenderer;

    public MoveCommand(
        GameService gameService,
        StatsService statsService,
        AgentService agentService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("move", "Makes a move on user's current game", "move");
        this.gameService = gameService;
        this.statsService = statsService;
        this.agentService = agentService;
        this.boardRenderer = boardRenderer;
    }

    /**
     * Sends a move made message to a given channel
     * @param channel to send message to
     * @param game included in message
     * @param move included in message
     */
    public void sendGameMessage(MessageChannel channel, Game game, Tile move) {
        // render board and send back message
        BufferedImage image = boardRenderer.drawBoardMoves(game.getBoard());
        new GameViewMessageSender()
            .setGame(game, move)
            .setTag(game)
            .setImage(image)
            .sendMessage(channel);
    }

    /**
     * Sends a game view made message to a given channel
     * @param channel to send message to
     * @param game included in message
     */
    public void sendGameMessage(MessageChannel channel, Game game) {
        // render board and send back message
        BufferedImage image = boardRenderer.drawBoardMoves(game.getBoard());
        new GameViewMessageSender()
            .setGame(game)
            .setImage(image)
            .sendMessage(channel);
    }

    /**
     * Sends a game over message to a given channel by fetching and updating the stats for the users
     * @param channel to send message to
     * @param game included in message
     * @param move included in message
     */
    public void sendGameOverMessage(MessageChannel channel, Game game, Tile move) {
        // update elo the elo of the players
        GameResult result = game.getResult();
        statsService.updateStats(result);
        // render board and send back message
        BufferedImage image = boardRenderer.drawBoard(game.getBoard());
        new GameOverMessageSender()
            .setGame(result)
            .addMoveMessage(result.getWinner(), move.toString())
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
            .setTag(result)
            .setImage(image)
            .sendMessage(channel);
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        String strMove = ctx.getParam("move");
        Player player = new Player(event.getAuthor());

        Tile move = new Tile(strMove);
        try {
            // make player's move, then respond accordingly depending on the new game state
            Game game = gameService.makeMove(player, move);

            if (!game.isGameOver()) {
                if (!game.isAgainstBot()) {
                    // not game over not against bot
                    sendGameMessage(channel, game, move);
                } else {
                    // not game over against bot
                    sendGameMessage(channel, game);
                    // queue an ai request which will find the best move, make the move, and send back a response
                    int depth = BotUtils.getDepthFromId(game.getCurrentPlayer().getId());
                    agentService.findBestMove(
                        new AgentRequest<>(game, depth, (Move bestMove) -> {
                            // make the ai's best move on the game state, and update in storage
                            gameService.makeMove(game, bestMove.getTile());
                            // check if game is over after ai makes move
                            if (!game.isGameOver()) {
                                sendGameMessage(channel, game, bestMove.getTile());
                            } else {
                                sendGameOverMessage(channel, game, bestMove.getTile());
                            }
                        })
                    );
                }
            } else {
                // game over against bot or not
                sendGameOverMessage(channel, game, move);
            }

            logger.info("Player " + player + " made move on game");
        } catch (TurnException e) {
            channel.sendMessage("It isn't your turn.").queue();
        } catch (NotPlayingException e) {
            channel.sendMessage("You're not currently in a game.").queue();
        } catch (InvalidMoveException e) {
            channel.sendMessage("Can't make a move to " + strMove + ".").queue();
        }
    }
}