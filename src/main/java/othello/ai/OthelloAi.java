package othello.ai;

import othello.board.OthelloBoard;
import othello.board.Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OthelloAi
{
    private static final float INF = Float.MAX_VALUE;
    private static final int TT_SIZE = (int) Math.pow(2, 16) + 1;
    private static final int CL_SIZE = 10;

    private final int maxDepth;
    private final OthelloBoard rootBoard;

    private final ZHasher hasher;
    private final TTable tTable;

    public OthelloAi(int boardSize, int maxDepth) {
        this.rootBoard = new OthelloBoard(boardSize);
        this.maxDepth = maxDepth;
        this.hasher = new ZHasher(boardSize);
        this.tTable = new TTable(TT_SIZE, CL_SIZE);
    }

    public OthelloAi(OthelloBoard board, int maxDepth) {
        this.rootBoard = board.copy();
        this.maxDepth = maxDepth;
        this.hasher = new ZHasher(board.getBoardSize());
        this.tTable = new TTable(TT_SIZE, CL_SIZE);
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public OthelloBoard getRootBoard() {
        return rootBoard;
    }

    public List<Move> findRankedMoves() {
        List<Tile> moves = rootBoard.findPotentialMoves();
        List<Move> rankedMoves = new ArrayList<>();

        // call the iterative deepening negamax to calculate the heuristic for each move and add it to list
        for (Tile move : moves) {
            OthelloBoard copiedBoard = rootBoard.copy();
            copiedBoard.makeMove(move);

            float heuristic = negamaxIDDFS(copiedBoard, maxDepth - 1);
            rankedMoves.add(new Move(move, heuristic));
        }

        Comparator<Move> comparator = rootBoard.isBlackMove() ?
            (m1, m2) -> Float.compare(m2.getHeuristic(), m1.getHeuristic()) :
            (m1, m2) -> Float.compare(m1.getHeuristic(), m2.getHeuristic());
        rankedMoves.sort(comparator);

        return rankedMoves;
    }

    public Move findBestMove() {
        List<Tile> moves = rootBoard.findPotentialMoves();
        Tile bestMove = null;
        float bestHeuristic = -INF;

        // call the iterative deepening negamax to calculate the heuristic for each potential move and determine the best one
        for (Tile move : moves) {
            OthelloBoard copiedBoard = rootBoard.copy();
            copiedBoard.makeMove(move);

            float heuristic = negamaxIDDFS(copiedBoard, maxDepth - 1);
            if (heuristic > bestHeuristic) {
                bestMove = move;
                bestHeuristic = heuristic;
            }
        }

        return new Move(bestMove, bestHeuristic);
    }

    /**
     * Searches othello game tree with iterative deepening depth first search
     * Starts from a relative depth of 1 until a specified relative max depth
     */
    public float negamaxIDDFS(OthelloBoard board, int maxDepth) {
        float heuristic = 0;
        for (int depthLimit = 1; depthLimit < maxDepth; depthLimit++) {
            heuristic = negamax(board.copy(), depthLimit, board.isBlackMove(), -INF, INF);
        }
        return heuristic;
    }

    /**
     * Searches othello game tree DLS with alpha beta pruning to evaluate how good a board is
     */
    public float negamax(OthelloBoard board, int depth, boolean maximizer, float alpha, float beta) {
        List<Tile> moves = board.findPotentialMoves();

        // stop when we reach depth floor or cannot expand node's children
        if (depth == 0 || moves.isEmpty()) {
            return board.heuristic();
        }

        // find the children for the board state
        List<OthelloBoard> children = new ArrayList<>();
        // create a new child board with a corresponding node for each move
        for (Tile move : moves) {
            OthelloBoard copiedBoard = board.copy();
            copiedBoard.makeMove(move);
            children.add(copiedBoard);
        }

        if (maximizer) {
            // explore best children first for move ordering, find the best moves and return them
            for (OthelloBoard child : children) {
                alpha = Math.max(alpha, negamax(child, depth - 1, false, alpha, beta));
                // prune this branch, it cannot possibly be better than any child found so far
                if (alpha >= beta) {
                    break;
                }
            }
            // empty the board to save memory, a node expanded once never needs to be expanded again
            board.setBoardEmpty();
            return alpha;
        } else {
            // explore best children first for move ordering, find the best moves and return them
            for (OthelloBoard child : children) {
                beta = Math.min(beta, negamax(child, depth - 1, true, alpha, beta));
                // prune this branch, it cannot possibly be better than any child found so far
                if (beta <= alpha) {
                    break;
                }
            }
            // empty the board to save memory, a node expanded once never needs to be expanded again
            board.setBoardEmpty();
            return beta;
        }
    }
}
