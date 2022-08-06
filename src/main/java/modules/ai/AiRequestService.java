package modules.ai;

import othello.ai.Move;
import othello.ai.OthelloAi;

import java.util.List;
import java.util.concurrent.*;

public class AiRequestService
{
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public void findRankedMoves(AiRequest<List<Move>> aiRequest) {
        executorService.submit(() -> {
            List<Move> moves = new OthelloAi(aiRequest.getBoard(), aiRequest.getDepth()).findRankedMoves();
            aiRequest.getOnComplete().accept(moves);
        });
    }

    public void findBestMove(AiRequest<Move> aiRequest) {
        executorService.submit(() -> {
            Move move = new OthelloAi(aiRequest.getBoard(), aiRequest.getDepth()).findBestMove();
            aiRequest.getOnComplete().accept(move);
        });
    }
}
