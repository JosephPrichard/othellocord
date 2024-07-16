/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import commands.context.CommandContext;
import commands.views.GameResultView;
import commands.views.GameStateView;
import commands.views.GameView;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import othello.BoardRenderer;
import othello.OthelloBoard;
import services.agent.AgentDispatcher;
import services.game.Game;
import services.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

import static utils.Logger.LOGGER;

public class SimulateCommand extends Command {

    private final ScheduledExecutorService scheduler;
    private final AgentDispatcher agentDispatcher;

    public SimulateCommand(AgentDispatcher agentDispatcher, ScheduledExecutorService scheduler) {
        this.agentDispatcher = agentDispatcher;
        this.scheduler = scheduler;
    }

    private void gameLoop(Game game, BlockingQueue<Optional<GameView>> queue, String id) {
        int depth = Player.Bot.getDepthFromId(game.getCurrentPlayer().id());

        final var board = game.board();
        agentDispatcher.findMove(board, depth, (bestMove) -> {
            try {
                var nextGame = Game.from(game);
                nextGame.makeMove(bestMove.tile());

                var image = BoardRenderer.drawBoardMoves(nextGame.board());

                if (nextGame.isOver()) {
                    var view = GameResultView.createSimulationView(nextGame, bestMove.tile(), image);
                    queue.put(Optional.of(view));
                    queue.put(Optional.empty());

                    LOGGER.info("Finished the game simulation: " + id);
                } else {
                    var view = GameStateView.createSimulationView(nextGame, bestMove.tile(), image);
                    queue.put(Optional.of(view));

                    // each completion callback will recursively schedule the next action
                    gameLoop(nextGame, queue, id);
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "Failed to put a task on the game view queue", ex);
            }
        });
    }

    private void waitLoop(BlockingQueue<Optional<GameView>> queue, CommandContext ctx) {
        Runnable scheduled = () -> {
            try {
                var optView = queue.take();
                if (optView.isPresent()) {
                    ctx.sendView(optView.get());
                    // each completion callback will recursively schedule the next action
                    waitLoop(queue, ctx);
                }
                else {
                    LOGGER.info("Finished game simulation wait loop");
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error occurred in scheduled event ", ex);
            }
        };
        // wait at least 1 second before we process each element to avoid overloading a Discord text channel
        scheduler.schedule(scheduled, 1000, TimeUnit.MILLISECONDS);
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

        var id = UUID.randomUUID().toString();
        LOGGER.info("Starting the game simulation: " + id);

        var image = BoardRenderer.drawBoardMoves(startGame.board());
        var startView = GameStateView.createSimulationStartView(startGame, image);
        ctx.replyView(startView);

        BlockingQueue<Optional<GameView>> queue = new LinkedBlockingQueue<>();
        gameLoop(startGame, queue, id);
        waitLoop(queue, ctx);
    }
}
