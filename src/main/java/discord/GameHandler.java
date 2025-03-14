/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import engine.BoardRenderer;
import engine.OthelloBoard;
import engine.Tile;
import lombok.AllArgsConstructor;
import models.Game;
import models.Player;
import models.Stats;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import services.GameService;
import services.StatsService;
import utils.EventUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class GameHandler {
    private BotState state;

    public void handleView(SlashCommandInteraction event) {
        GameService gameService = state.getGameService();
        Player player = new Player(event.getUser());

        Game game = gameService.getGame(player);
        if (game == null) {
            event.reply("You're not currently in a game.").queue();
            return;
        }

        OthelloBoard board = game.getBoard();
        List<Tile> potentialMoves = board.findPotentialMoves();

        BufferedImage image = BoardRenderer.drawBoard(board, potentialMoves);
        GameView view = GameView.createGameView(game, image);
        EventUtils.replyView(event, view);

        LOGGER.info("{} viewed moves in game", player);
    }

    private GameView handleGameOver(Game game, Tile move) {
        Game.Result result = game.createResult();
        Stats.Result statsResult = state.getStatsService().writeStats(result);

        BufferedImage image = BoardRenderer.drawBoard(game.getBoard());
        return GameView.createGameOverView(result, statsResult, move, game, image);
    }

    private void makeBotMove(SlashCommandInteraction event, Game game) {
        Player currPlayer = game.getCurrentPlayer();
        int depth = Player.Bot.getDepthFromId(currPlayer.id);

        try {
            // queue an agent request which will find the best move, make the move, and send back a response
            Future<Tile.Move> future = state.getAgentDispatcher().findMove(game.getBoard(), depth);
            Tile.Move bestMove = future.get();

            Game newGame = state.getGameService().makeMove(currPlayer, bestMove.tile());

            GameView view = newGame.isOver() ?
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
        String strMove = Objects.requireNonNull(EventUtils.getStringParam(event, "move"));
        Player player = new Player(event.getUser());

        Tile move = Tile.fromNotation(strMove);
        try {
            Game game = state.getGameService().makeMove(player, move);
            LOGGER.info("{} made move on game {} to {}", player, game, move);

            if (game.isOver()) {
                GameView view = handleGameOver(game, move);
                EventUtils.replyView(event, view);
            } else {
                if (game.isAgainstBot()) {
                    GameView view = GameView.createGameMoveView(game, BoardRenderer.drawBoardMoves(game.getBoard()));
                    EventUtils.replyView(event, view);

                    makeBotMove(event, game);
                } else {
                    GameView view = GameView.createGameMoveView(game, move, BoardRenderer.drawBoardMoves(game.getBoard()));
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
        GameService gameService = state.getGameService();
        StatsService statsService = state.getStatsService();

        Player player = new Player(event.getUser());

        Game game = gameService.getGame(player);
        if (game == null) {
            event.reply("You're not currently in a game.").queue();
            return;
        }

        gameService.deleteGame(game);
        Game.Result result = game.createForfeitResult(player);

        Stats.Result statsResult = statsService.writeStats(result);

        BufferedImage image = BoardRenderer.drawBoard(game.getBoard());
        GameView view = GameView.createForfeitView(result, statsResult, image);
        EventUtils.replyView(event, view);

        LOGGER.info("Player: {} has forfeited", player);
    }
}
