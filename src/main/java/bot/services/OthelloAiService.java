package bot.services;

import othello.ai.Move;
import othello.ai.OthelloAi;
import othello.board.OthelloBoard;

import java.util.List;

public class OthelloAiService
{
    public List<Move> findRankedMoves(OthelloBoard board, int depth) {
        return new OthelloAi(board, depth).findRankedMoves();
    }
}
