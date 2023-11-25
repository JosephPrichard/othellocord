/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import services.AgentRequest;
import services.AgentService;
import services.StatsService;
import services.Game;
import services.GameService;
import services.GameResult;
import services.Player;
import discord.message.GameViewSender;
import discord.message.GameOverSender;
import discord.renderers.OthelloBoardRenderer;
import services.exceptions.InvalidMoveException;
import services.exceptions.NotPlayingException;
import services.exceptions.TurnException;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import othello.Move;
import othello.Tile;
import utils.Bot;

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

    public void sendGameMessage(MessageChannel channel, Game game, Tile move) {
        // render board and send back message
        var image = boardRenderer.drawBoardMoves(game.getBoard());
        new GameViewSender()
            .setGame(game, move)
            .setTag(game)
            .setImage(image)
            .sendMessage(channel);
    }

    public void sendGameMessage(MessageChannel channel, Game game) {
        // render board and send back message
        var image = boardRenderer.drawBoardMoves(game.getBoard());
        new GameViewSender()
            .setGame(game)
            .setImage(image)
            .sendMessage(channel);
    }

    public void sendGameOverMessage(MessageChannel channel, Game game, Tile move) {
        // update elo the elo of the players
        var result = game.getResult();
        statsService.updateStats(result);
        // render board and send back message
        var image = boardRenderer.drawBoard(game.getBoard());
        new GameOverSender()
            .setGame(result)
            .addMoveMessage(result.getWinner(), move.toString())
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
            .setTag(result)
            .setImage(image)
            .sendMessage(channel);
    }

    private void sendAgentRequest(MessageChannel channel, Game game) {
        // queue an agent request which will find the best move, make the move, and send back a response
        var depth = Bot.getDepthFromId(game.getCurrentPlayer().getId());
        var r = new AgentRequest<Move>(game, depth, (Move bestMove) -> {
            // make the agent's best move on the game state, and update in storage
            gameService.makeMove(game, bestMove.getTile());
            // check if game is over after agent makes move
            if (!game.isGameOver()) {
                sendGameMessage(channel, game, bestMove.getTile());
            } else {
                sendGameOverMessage(channel, game, bestMove.getTile());
            }
        });
        agentService.findBestMove(r);
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var event = ctx.getEvent();
        var channel = event.getChannel();

        var strMove = ctx.getParam("move");
        var player = new Player(event.getAuthor());

        var move = new Tile(strMove);
        try {
            // make player's move, then respond accordingly depending on the new game state
            var game = gameService.makeMove(player, move);

            if (!game.isGameOver()) {
                if (!game.isAgainstBot()) {
                    // not game over not against bot
                    sendGameMessage(channel, game, move);
                } else {
                    // not game over against bot
                    sendGameMessage(channel, game);
                    sendAgentRequest(channel, game);
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