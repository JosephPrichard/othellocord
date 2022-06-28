package bot.services;

import bot.dao.GameDao;
import bot.dtos.AiRequestDto;
import othello.ai.Move;
import othello.ai.OthelloAi;

import java.util.List;
import java.util.concurrent.*;

public class OthelloAiService
{
    private final ExecutorService executorService = Executors.newFixedThreadPool(30);

    public void findRankedMoves(AiRequestDto<List<Move>> aiRequestDto) {
        executorService.submit(() -> {
            List<Move> moves = new OthelloAi(aiRequestDto.getBoard(), aiRequestDto.getDepth()).findRankedMoves();
            aiRequestDto.getOnComplete().accept(moves);
        });
    }

    public void findBestMove(AiRequestDto<Move> aiRequestDto) {
        executorService.submit(() -> {
            Move move = new OthelloAi(aiRequestDto.getBoard(), aiRequestDto.getDepth()).findBestMove();
            aiRequestDto.getOnComplete().accept(move);
        });
    }
}
