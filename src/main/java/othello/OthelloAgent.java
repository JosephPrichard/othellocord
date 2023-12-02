/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public final class OthelloAgent
{
    private final Logger logger = Logger.getLogger("othello.ai");

    private static final float INF = Float.MAX_VALUE;

    private final int maxTime;
    private long stopTime = 0;
    private final int maxDepth;

    private final OthelloEvaluator evaluator;
    private final OthelloBoard rootBoard;

    private final ZHasher hasher;
    private final TTable tTable;

    public OthelloAgent(OthelloBoard board, int maxDepth) {
        this(board, maxDepth, (int) Math.pow(2, 12) + 1, 3000);
    }

    public OthelloAgent(OthelloBoard board, int maxDepth, int ttSize, int maxTime) {
        this.rootBoard = board.copy();
        this.maxDepth = maxDepth;
        this.hasher = new ZHasher();
        this.evaluator = new OthelloEvaluator();
        this.tTable = new TTable(ttSize);
        this.maxTime = maxTime;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public OthelloBoard getRootBoard() {
        return rootBoard;
    }

    public List<Move> findRankedMoves() {
        var moves = rootBoard.findPotentialMoves();
        List<Move> rankedMoves = new ArrayList<>();

        // call the iterative deepening negamax to calculate the heuristic for each move and add it to list
        for (var move : moves) {
            var copiedBoard = rootBoard.copy();
            copiedBoard.makeMove(move);

            var heuristic = iterativeAbMinimax(copiedBoard, maxDepth - 1);
            rankedMoves.add(new Move(move, heuristic));
        }

        // sort the moves to rank them properly
        Comparator<Move> comparator = rootBoard.isBlackMove() ?
            (m1, m2) -> Float.compare(m2.getHeuristic(), m1.getHeuristic()) :
            (m1, m2) -> Float.compare(m1.getHeuristic(), m2.getHeuristic());
        rankedMoves.sort(comparator);

        // remove duplicate moves (this is possible, has minimal effect on speed of algo due to transposition tables)
        var isDup = new boolean[OthelloBoard.getBoardSize()][OthelloBoard.getBoardSize()];
        for (var iterator = rankedMoves.iterator(); iterator.hasNext(); ) {
            var move = iterator.next();
            var r = move.getTile().getRow();
            var c = move.getTile().getCol();
            if (isDup[r][c]) {
                iterator.remove();
            }
            isDup[r][c] = true;
        }

        return rankedMoves;
    }

    public Move findBestMove() {
        var startTime = System.currentTimeMillis();
        stopTime = startTime + maxTime;

        var moves = rootBoard.findPotentialMoves();
        Tile bestMove = null;
        var bestHeuristic = rootBoard.isBlackMove() ? -INF : INF;

        // comparator to find the best move by heuristic
        Comparator<Float> comparator = rootBoard.isBlackMove() ? Float::compare : (m1, m2) -> Float.compare(m2, m1);

        // call the iterative deepening negamax to calculate the heuristic for each potential move and determine the best one
        for (var move : moves) {
            var copiedBoard = rootBoard.copy();
            copiedBoard.makeMove(move);

            var heuristic = iterativeAbMinimax(copiedBoard, maxDepth - 1);
            if (comparator.compare(heuristic, bestHeuristic) > 0) {
                bestMove = move;
                bestHeuristic = heuristic;
            }
        }

        var endTime = System.currentTimeMillis();
        var timeTaken = endTime - startTime;

        logger.info("Finished ai analysis, " +
            "max_depth: " + maxDepth + ", " +
            "tt_hits: " + tTable.getHits() + ", " +
            "tt_misses: " + tTable.getMisses() + ", " +
            "time_taken: " + timeTaken + "ms"
        );

        return new Move(bestMove, bestHeuristic);
    }

    /**
     * Searches othello game tree with iterative deepening depth first search
     * Starts from a relative depth of 1 until a specified relative max depth
     */
    public float iterativeAbMinimax(OthelloBoard board, int maxDepth) {
        float heuristic = 0;
        for (var depthLimit = 1; depthLimit < maxDepth; depthLimit++) {
            heuristic = abMinimax(board.copy(), depthLimit, board.isBlackMove(), -INF, INF);
        }
        return heuristic;
    }

    /**
     * Searches othello game tree using minimax with alpha beta pruning to evaluate how good a board is
     */
    public float abMinimax(OthelloBoard board, int depth, boolean maximizer, float alpha, float beta) {
        var moves = board.findPotentialMoves();

        // stop when we reach depth floor, cannot expand node's children, or we've gone over time
        // depth 5 is trivial to search up to
        var minDepth = 5;
        if (depth == 0 || moves.isEmpty()
            || (depth >= minDepth && System.currentTimeMillis() > stopTime)
        ) {
            return evaluator.heuristic(board);
        }

        var hashKey = hasher.hash(board);

        // check tt table to see if we have a cache hit
        var node = tTable.get(hashKey);
        if (node != null && node.getDepth() >= depth) {
            return node.getHeuristic();
        }

        // find the children for the board state
        List<OthelloBoard> children = new ArrayList<>();
        // create a new child board with a corresponding node for each move
        for (var move : moves) {
            var copiedBoard = board.copy();
            copiedBoard.makeMove(move);
            children.add(copiedBoard);
        }

        if (maximizer) {
            // explore best children first for move ordering, find the best moves and return them
            for (var child : children) {
                alpha = Math.max(alpha, abMinimax(child, depth - 1, false, alpha, beta));
                // prune this branch, it cannot possibly be better than any child found so far
                if (alpha >= beta) {
                    break;
                }
            }
            tTable.put(new TTNode(hashKey, alpha, depth));
            return alpha;
        } else {
            // explore best children first for move ordering, find the best moves and return them
            for (var child : children) {
                beta = Math.min(beta, abMinimax(child, depth - 1, true, alpha, beta));
                // prune this branch, it cannot possibly be better than any child found so far
                if (beta <= alpha) {
                    break;
                }
            }
            tTable.put(new TTNode(hashKey, beta, depth));
            return beta;
        }
    }

    public static void main(String[] args) {
        var startTime = System.currentTimeMillis();

        var board = new OthelloBoard();
        for (var j = 0; j < 10; j++) {
            var ai = new OthelloAgent(board, 8);
            var bestMove = ai.findBestMove();
            System.out.println(bestMove);
            board.makeMove(bestMove.getTile());
        }

        var endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) + " ms");
    }
}
