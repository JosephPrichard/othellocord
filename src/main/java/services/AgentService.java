/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import othello.Move;
import othello.OthelloAgent;

import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static utils.Logger.LOGGER;

public class AgentService
{
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public void findRankedMoves(AgentRequest<List<Move>> agentRequest) {
        executorService.submit(() -> {
            var agent = new OthelloAgent(agentRequest.getGame().getBoard(), agentRequest.getDepth());
            var moves = agent.findRankedMoves();
            agentRequest.getOnComplete().accept(moves);
        });
        LOGGER.info("Started agent ranked moves calculation of depth " + agentRequest.getDepth());
    }

    public void findBestMove(AgentRequest<Move> agentRequest) {
        executorService.submit(() -> {
            var agent = new OthelloAgent(agentRequest.getGame().getBoard(), agentRequest.getDepth());
            var move = agent.findBestMove();
            agentRequest.getOnComplete().accept(move);
        });
        LOGGER.info("Started agent best move calculation of depth " + agentRequest.getDepth());
    }
}
