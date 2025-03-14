/*
 * Copyright (c) Joseph Prichard 2024.
 */

package services;

import engine.OthelloAgent;
import engine.OthelloBoard;
import engine.Tile;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class AgentDispatcher {
    private final ExecutorService cpuBndExecutor;
    private final BlockingQueue<OthelloAgent> agentsQueue;

    public AgentDispatcher(ThreadPoolExecutor cpuBndExecutor) {
        this(cpuBndExecutor, new LinkedBlockingQueue<>());

        int agentCount = cpuBndExecutor.getMaximumPoolSize();
        assert agentCount > 0;

        for (int i = 0; i < agentCount; i++) {
            agentsQueue.add(new OthelloAgent());
        }
    }

    public Future<List<Tile.Move>> findMoves(OthelloBoard board, int depth) {
        CompletableFuture<List<Tile.Move>> future = new CompletableFuture<>();
        cpuBndExecutor.submit(() -> {
            try {
                OthelloAgent agent = agentsQueue.take();
                LOGGER.info("Started agent ranked moves calculation of depth {}", depth);

                List<Tile.Move> moves = agent.findRankedMoves(board, depth);
                agentsQueue.add(agent);

                LOGGER.info("Finished agent ranked moves calculation of depth {}: {}", depth,
                    moves.stream().map(Tile.Move::toString).collect(Collectors.joining(", ")));
                future.complete(moves);
            } catch (Exception ex) {
                LOGGER.warn("Error occurred while processing a find moves event ", ex);
            }
        });
        return future;
    }

    public Future<Tile.Move> findMove(OthelloBoard board, int depth) {
        CompletableFuture<Tile.Move> future = new CompletableFuture<>();
        cpuBndExecutor.submit(() -> {
            try {
                OthelloAgent agent = agentsQueue.take();

                LOGGER.info("Started agent best move calculation of depth {}", depth);

                Tile.Move move = agent.findBestMove(board, depth);
                agentsQueue.add(agent);

                LOGGER.info("Finished agent best move calculation of depth {}: {}", depth, move);
                future.complete(move);
            } catch (Exception ex) {
                LOGGER.warn("Error occurred while processing a find move event ", ex);
            }
        });
        return future;
    }
}
