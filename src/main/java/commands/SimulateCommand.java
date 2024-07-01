/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import commands.context.CommandContext;
import commands.messaging.GameResultView;
import commands.messaging.GameStateView;
import commands.messaging.GameView;
import othello.BoardRenderer;
import othello.OthelloBoard;
import services.agent.AgentDispatcher;
import services.game.Game;
import services.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static utils.Logger.LOGGER;

public class SimulateCommand extends Command {

    private final AgentDispatcher agentDispatcher;

    public SimulateCommand(AgentDispatcher agentDispatcher) {
        this.agentDispatcher = agentDispatcher;
    }

    public void gameLoop(Game game, BlockingQueue<Optional<GameView>> queue, String id) {
        int depth = Player.Bot.getDepthFromId(game.getCurrentPlayer().id());

        final var board = game.board();
        agentDispatcher.findMove(board, depth, (bestMove) -> {
            var nextGame = Game.from(game);
            nextGame.makeMove(bestMove.tile());

            try {
                var image = BoardRenderer.drawBoardMoves(nextGame.board());
                if (nextGame.isOver()) {
                    var view = GameResultView.createSimulationView(game.createResult(), bestMove.tile(), game, image);
                    queue.put(Optional.of(view));
                    queue.put(Optional.empty());

                    LOGGER.info("Finished the game simulation: " + id);
                } else {
                    var view = GameStateView.createGameView(game, image);
                    queue.put(Optional.of(view));

                    gameLoop(nextGame, queue, id);
                }
            } catch (InterruptedException ex) {
                LOGGER.warning("Failed to insert game view on queue " + ex);
            }
        });
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var blackLevel = ctx.getLongParam("blevel");
        if (blackLevel == null) {
            blackLevel = 3L;
        }

        var whiteLevel = ctx.getLongParam("wlevel");
        if (whiteLevel == null) {
            whiteLevel = 3L;
        }

        var startGame = new Game(OthelloBoard.initial(), Player.Bot.create(blackLevel), Player.Bot.create(whiteLevel));

        var image = BoardRenderer.drawBoardMoves(startGame.board());
        var startView = GameStateView.createGameStartView(startGame, image);
        ctx.sendReply(startView);

        var id = UUID.randomUUID().toString();

        LOGGER.info("Starting the game simulation: " + id);

        BlockingQueue<Optional<GameView>> queue = new LinkedBlockingQueue<>();
        gameLoop(startGame, queue, id);

        // wait for the views to come in on the loop thread in the calling thread (an IO thread)
        try {
            while (true) {
                var elemView = queue.take();
                if (elemView.isPresent()) {
                    // wait at least 1 second before we process another element to avoid overloading a Discord text channel
                    Thread.sleep(1000);
                    ctx.sendMessage(elemView.get());
                } else {
                    break;
                }
            }
            LOGGER.info("Finished sending game simulation views");
        } catch (InterruptedException ex) {
            LOGGER.warning("Failed to take game view off queue " + ex);
        }
    }
}
