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
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import services.AgentDispatcher;
import services.GameService;
import utils.EventUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static models.Player.Bot.MAX_BOT_LEVEL;
import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class AgentHandler {
    public static final long MAX_DELAY = 5000L;
    public static final long MIN_DELAY = 1000L;

    private GameService gameService;
    private AgentDispatcher agentDispatcher;
    private ScheduledExecutorService scheduler;
    private ExecutorService taskExecutor;

    public void handleAnalyze(SlashCommandInteraction event) {
        Long level = EventUtils.getLongParam(event, "level");
        if (level == null) {
            level = 3L;
        }

        // check if level is within range
        if (Player.Bot.isInvalidLevel(level)) {
            event.reply(String.format("Invalid level, should be between 1 and %s", MAX_BOT_LEVEL)).queue();
            return;
        }

        Player player = new Player(event.getUser());

        Game game = gameService.getGame(player);
        if (game == null) {
            event.reply("You're not currently in a game.").queue();
            return;
        }

        // send starting message, then add queue an agent request, send back the results in a message when it's done
        int depth = Player.Bot.getDepthFromId(level);
        final Long finalLevel = level;
        event.reply("Analyzing... Wait a second...")
            .queue(hook -> {
                LOGGER.info("Starting board state analysis");

                try {
                    Future<List<Tile.Move>> future = agentDispatcher.findMoves(game.getBoard(), depth);
                    List<Tile.Move> rankedMoves = future.get();

                    BufferedImage image = BoardRenderer.drawBoardAnalysis(game.getBoard(), rankedMoves);
                    GameView view = GameView.createAnalysisView(game, image, finalLevel, player);

                    view.editUsingHook(hook);
                    LOGGER.info("Finished board state analysis");
                } catch (ExecutionException | InterruptedException e) {
                    LOGGER.warn("Error occurred while responding to an analyze command", e);
                }
            });
    }

    private void simulationGameLoop(Game initialGame, BlockingQueue<Optional<GameView>> queue, String id) {
        int depth = Player.Bot.getDepthFromId(initialGame.getCurrentPlayer().getId());

        taskExecutor.submit(() -> {
            Game game = initialGame;

            boolean finished = false;
            while (!finished) {
                try {
                    OthelloBoard board = game.getBoard();
                    Future<Tile.Move> future = agentDispatcher.findMove(board, depth);
                    Tile.Move bestMove = future.get();

                    Game nextGame = Game.from(game);
                    nextGame.makeMove(bestMove.tile());

                    BufferedImage image = BoardRenderer.drawBoardMoves(nextGame.getBoard());

                    if (nextGame.isOver()) {
                        GameView view = GameView.createResultSimulationView(nextGame, bestMove.tile(), image);
                        queue.put(Optional.of(view));
                        queue.put(Optional.empty());

                        LOGGER.info("Finished the game simulation: {}", id);
                        finished = true;
                    } else {
                        GameView view = GameView.createSimulationView(nextGame, bestMove.tile(), image);
                        queue.put(Optional.of(view));
                        game = nextGame;
                    }
                } catch (InterruptedException | ExecutionException x) {
                    LOGGER.error("Failed to put a task on the game view queue", x);
                }
            }
        });
    }

    private void simulationWaitLoop(BlockingQueue<Optional<GameView>> queue, long delay, InteractionHook hook) {
        Runnable scheduled = () -> {
            try {
                Optional<GameView> optView = queue.take();
                if (optView.isPresent()) {
                    // each completion callback will recursively schedule the next action
                    optView.get().editUsingHook(hook);
                    simulationWaitLoop(queue, delay, hook);
                } else {
                    LOGGER.info("Finished game simulation wait loop");
                }
            } catch (Exception ex) {
                LOGGER.error("Error occurred in scheduled event", ex);
            }
        };
        // wait at least 1 second before we process each element to avoid overloading a Discord text channel
        scheduler.schedule(scheduled, delay, TimeUnit.MILLISECONDS);
    }

    public void handleSimulate(SlashCommandInteraction event) {
        Long blackLevel = EventUtils.getLongParam(event, "black-level");
        if (blackLevel == null) {
            blackLevel = 3L;
        }

        Long whiteLevel = EventUtils.getLongParam(event, "white-level");
        if (whiteLevel == null) {
            whiteLevel = 3L;
        }

        Long delay = EventUtils.getLongParam(event, "delay");
        if (delay == null) {
            delay = 1500L;
        }

        long finalDelay = delay;
        if (finalDelay < MIN_DELAY || finalDelay > MAX_DELAY) {
            event.reply(String.format("Invalid delay, should be between %s and %s ms", MIN_DELAY, MAX_DELAY)).queue();
            return;
        }

        Game startGame = new Game(OthelloBoard.initial(), Player.Bot.create(blackLevel), Player.Bot.create(whiteLevel));

        String id = UUID.randomUUID().toString();
        LOGGER.info("Starting the game simulation: {}", id);

        BufferedImage image = BoardRenderer.drawBoardMoves(startGame.getBoard());
        GameView startView = GameView.createSimulationStartView(startGame, image);

        EventUtils.replyView(event, startView, (hook) -> {
            BlockingQueue<Optional<GameView>> queue = new LinkedBlockingQueue<>();
            simulationGameLoop(startGame, queue, id);
            simulationWaitLoop(queue, finalDelay, hook);
        });
    }
}
