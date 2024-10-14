/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import engine.BoardRenderer;
import engine.Tile;
import lombok.AllArgsConstructor;
import models.Game;
import models.Player;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import services.GameService;
import utils.EventUtils;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class GameHandler {
    private BotState state;

    public void handleView(SlashCommandInteraction event) {
        var gameService = state.getGameService();
        var player = new Player(event.getUser());

        var game = gameService.getGame(player);
        if (game == null) {
            event.reply("You're not currently in a game.").queue();
            return;
        }

        var board = game.getBoard();
        var potentialMoves = board.findPotentialMoves();

        var image = BoardRenderer.drawBoard(board, potentialMoves);
        var view = GameView.createGameView(game, image);
        EventUtils.replyView(event, view);

        LOGGER.info("{} viewed moves in game", player);
    }

    private GameView handleGameOver(Game game, Tile move) {
        var result = game.createResult();
        var statsResult = state.getStatsService().writeStats(result);

        var image = BoardRenderer.drawBoard(game.getBoard());
        return GameView.createGameOverView(result, statsResult, move, game, image);
    }

    private void makeBotMove(SlashCommandInteraction event, Game game) {
        var currPlayer = game.getCurrentPlayer();
        var depth = Player.Bot.getDepthFromId(currPlayer.id);

        try {
            // queue an agent request which will find the best move, make the move, and send back a response
            var future = state.getAgentDispatcher().findMove(game.getBoard(), depth);
            var bestMove = future.get();

            var newGame = state.getGameService().makeMove(currPlayer, bestMove.tile());

            var view = newGame.isOver() ?
                handleGameOver(newGame, bestMove.tile()) :
                GameView.createGameMoveView(game, bestMove.tile(), BoardRenderer.drawBoardMoves(game.getBoard()));

            EventUtils.sendView(event, view);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Error occurred while waiting for a bot response ", e);
        } catch (GameService.TurnException | GameService.NotPlayingException | GameService.InvalidMoveException e) {
            // this shouldn't happen: the bot should only make legal moves when it is currently it's turn
            // if we get an error like this, the only thing we can do is log it and debug later
            LOGGER.error("Error occurred after handling a bot move", e);
        }
    }

    public void handleMove(SlashCommandInteraction event) {
        var strMove = Objects.requireNonNull(EventUtils.getStringParam(event, "move"));
        var player = new Player(event.getUser());

        var move = Tile.fromNotation(strMove);
        try {
            var game = state.getGameService().makeMove(player, move);
            LOGGER.info("{} made move on game {} to {}", player, game, move);

            if (game.isOver()) {
                var view = handleGameOver(game, move);
                EventUtils.replyView(event, view);
            } else {
                if (game.isAgainstBot()) {
                    var view = GameView.createGameMoveView(game, BoardRenderer.drawBoardMoves(game.getBoard()));
                    EventUtils.replyView(event, view);

                    makeBotMove(event, game);
                } else {
                    var view = GameView.createGameMoveView(game, move, BoardRenderer.drawBoardMoves(game.getBoard()));
                    EventUtils.replyView(event, view);
                }
            }
        } catch (GameService.TurnException e) {
            event.reply("It isn't your turn.").queue();
        } catch (GameService.NotPlayingException e) {
            event.reply("You're not currently in a game.").queue();
        } catch (GameService.InvalidMoveException e) {
            event.reply("Can't make a move to " + strMove + ".").queue();
        }
    }

    public void handleForfeit(SlashCommandInteraction event) {
        var gameService = state.getGameService();
        var statsService = state.getStatsService();

        var player = new Player(event.getUser());

        var game = gameService.getGame(player);
        if (game == null) {
            event.reply("You're not currently in a game.").queue();
            return;
        }

        gameService.deleteGame(game);
        var result = game.createForfeitResult(player);

        var statsResult = statsService.writeStats(result);

        var image = BoardRenderer.drawBoard(game.getBoard());
        var view = GameView.createForfeitView(result, statsResult, image);
        EventUtils.replyView(event, view);

        LOGGER.info("Player: {} has forfeited", player);
    }
}
