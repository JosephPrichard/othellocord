/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import lombok.AllArgsConstructor;
import models.Game;
import models.Player;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import domain.BoardRenderer;
import domain.OthelloBoard;
import domain.Tile;
import services.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static utils.Log.LOGGER;

@AllArgsConstructor
public class MoveCommand extends CommandHandler {

    private final GameService gameService;
    private final StatsService statsService;
    private final AgentDispatcher agentDispatcher;

    public GameView buildMoveView(Game game, Tile move) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return GameStateView.createGameView(game, move, image);
    }

    public GameView buildMoveView(Game game) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return GameStateView.createGameView(game, image);
    }

    public GameView onGameOver(Game game, Tile move) {
        var result = game.createResult();
        var statsResult = statsService.writeStats(result);
        var image = BoardRenderer.drawBoard(game.board());
        return GameResultView.createGameOverView(result, statsResult, move, game, image);
    }

    public void doBotMove(CommandContext ctx, Game game) {
        var currPlayer = game.currentPlayer();
        var depth = Player.Bot.getDepthFromId(currPlayer.id());

        try {
            // queue an agent request which will find the best move, make the move, and send back a response
            var future = agentDispatcher.findMove(game.board(), depth);
            var bestMove = future.get();

            var newGame = gameService.makeMove(currPlayer, bestMove.tile());

            var view = newGame.isOver() ?
                onGameOver(newGame, bestMove.tile()) :
                buildMoveView(newGame, bestMove.tile());

            ctx.sendView(view);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warning("Error occurred while waiting for a bot response " + e);
        } catch (GameService.TurnException | GameService.NotPlayingException | GameService.InvalidMoveException e) {
            // this shouldn't happen: the bot should only make legal moves when it is currently it's turn
            // if we get an error like this, the only thing we can do is log it and debug later
            LOGGER.warning("Error occurred after handling a bot move" + e);
        }
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var strMove = Objects.requireNonNull(ctx.getStringParam("move"));
        var player = ctx.getPlayer();

        var move = Tile.fromNotation(strMove);
        try {
            var game = gameService.makeMove(player, move);

            if (game.isOver()) {
                var view = onGameOver(game, move);
                ctx.replyView(view);
            } else {
                if (game.isAgainstBot()) {
                    var view = buildMoveView(game);
                    ctx.replyView(view);
                    doBotMove(ctx, game);
                } else {
                    var view = buildMoveView(game, move);
                    ctx.replyView(view);
                }
            }

            LOGGER.info("Player " + player + " made move on game");
        } catch (GameService.TurnException e) {
            ctx.reply("It isn't your turn.");
        } catch (GameService.NotPlayingException e) {
            ctx.reply("You're not currently in a game.");
        } catch (GameService.InvalidMoveException e) {
            ctx.reply("Can't make a move to " + strMove + ".");
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteraction interaction) {
        var player = new Player(interaction.getUser());

        var game = gameService.getGame(player);
        if (game != null) {
            var moves = game.findPotentialMoves();

            // don't display duplicate moves
            var duplicate = new boolean[OthelloBoard.getBoardSize()][OthelloBoard.getBoardSize()];

            List<Choice> choices = new ArrayList<>();
            for (var tile : moves) {
                var row = tile.row();
                var col = tile.col();

                if (!duplicate[row][col]) {
                    choices.add(new Choice(tile.toString(), tile.toString()));
                }
                duplicate[row][col] = true;
            }

            interaction.replyChoices(choices).queue();
        } else {
            interaction.replyChoices().queue();
        }
    }
}