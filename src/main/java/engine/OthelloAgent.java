/*
 * Copyright (c) Joseph Prichard 2023.
 */

package engine;

import java.util.*;

import static utils.LogUtils.LOGGER;

public final class OthelloAgent {

    private static final float INF = Float.MAX_VALUE;
    private static final int MIN_DEPTH = 5;
    public static final int[][] CORNERS = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
    public static final int[][] XC_SQUARES = {{1, 1}, {1, 6}, {6, 1}, {6, 6}, {0, 1}, {0, 6}, {7, 1}, {7, 6}, {1, 0}, {1, 7}, {6, 0}, {6, 7}};

    private final int maxTime;
    private long stopTime = 0;
    private int nodesVisited = 0;
    private final Deque<StackFrame> stack = new ArrayDeque<>();
    private final TTable table;

    public OthelloAgent() {
        this((int) Math.pow(2, 12) + 1, 3000);
    }

    public OthelloAgent(int ttSize, int maxTime) {
        this.table = new TTable(ttSize);
        this.maxTime = maxTime;
    }

    public List<Tile.Move> findRankedMoves(OthelloBoard board, int maxDepth) {
        var startTime = System.currentTimeMillis();
        nodesVisited = 0;
        stopTime = startTime + maxTime;

        var moves = board.findPotentialMoves();

        List<Tile.Move> rankedMoves = new ArrayList<>();

        // call the iterative deepening negamax to calculate the heuristic for each move and add it to list
        for (var move : moves) {
            var child = board.makeMoved(move);
            var heuristic = evaluateLoop(child, maxDepth - 1);
            rankedMoves.add(new Tile.Move(move, heuristic));
        }

        // sort the moves to rank them properly
        Comparator<Tile.Move> comparator = board.isBlackMove() ?
            (m1, m2) -> Float.compare(m2.heuristic(), m1.heuristic()) :
            (m1, m2) -> Float.compare(m1.heuristic(), m2.heuristic());
        rankedMoves.sort(comparator);

        // remove duplicate moves (this is possible, has minimal effect on speed of algo due to transposition tables)
        var duplicate = new boolean[OthelloBoard.getBoardSize()][OthelloBoard.getBoardSize()];
        for (var iterator = rankedMoves.iterator(); iterator.hasNext(); ) {
            var move = iterator.next();
            var row = move.tile().row();
            var col = move.tile().col();

            if (duplicate[row][col])
                iterator.remove();
            duplicate[row][col] = true;
        }

        table.clear();
        return rankedMoves;
    }

    public Tile.Move findBestMove(OthelloBoard board, int maxDepth) {
        var startTime = System.currentTimeMillis();
        nodesVisited = 0;
        stopTime = startTime + maxTime;

        var moves = board.findPotentialMoves();

        Tile bestMove = null;
        var bestHeuristic = board.isBlackMove() ? -INF : INF;
        Comparator<Float> comparator = board.isBlackMove() ? Float::compare : (m1, m2) -> Float.compare(m2, m1);

        // call the iterative deepening negamax to calculate the heuristic for each potential move and determine the best one
        for (var move : moves) {
            var child = board.makeMoved(move);

            var heuristic = evaluateLoop(child, maxDepth - 1);
//            var heuristic = evaluate(child, maxDepth - 1);

            if (comparator.compare(heuristic, bestHeuristic) > 0) {
                bestMove = move;
                bestHeuristic = heuristic;
            }
        }

        var endTime = System.currentTimeMillis();
        var timeTaken = endTime - startTime;

        LOGGER.info("Finished ai analysis, max_depth: {}, nodes_visited: {}, tt_hits: {}, tt_misses: {}, time_taken: {}ms",
            maxDepth, nodesVisited, table.getHits(), table.getMisses(), timeTaken);

        table.clear();
        return new Tile.Move(bestMove, bestHeuristic);
    }

    public float evaluateLoop(OthelloBoard board, int maxDepth) {
        if (!stack.isEmpty()) {
            stack.clear();
        }

        float heuristic = 0;
        for (var depthLimit = 1; depthLimit < maxDepth; depthLimit++) {
            heuristic = evaluateLoop(stack, OthelloBoard.from(board), depthLimit);
        }
        return heuristic;
    }

    public float evaluate(OthelloBoard board, int maxDepth) {
        float heuristic = 0;
        for (var depthLimit = 1; depthLimit < maxDepth; depthLimit++) {
            heuristic = evaluate(OthelloBoard.from(board), depthLimit, -INF, INF);
        }
        return heuristic;
    }

    static class StackFrame {

        OthelloBoard board;
        int depth;
        float alpha;
        float beta;
        long hashKey;
        List<OthelloBoard> children = null;
        int index = 0;

        public StackFrame(OthelloBoard board, int depth, float alpha, float beta) {
            this.board = board;
            this.depth = depth;
            this.alpha = alpha;
            this.beta = beta;
        }

        public OthelloBoard nextBoard() {
            var board = children.get(index);
            children.set(index, null);
            index++;
            return board;
        }

        public boolean hasNext() {
            return index < children.size();
        }

        public boolean hasChildren() {
            return children != null;
        }
    }

    private float evaluateLoop(Deque<StackFrame> stack, OthelloBoard initialBoard, int startDepth) {
        stack.push(new StackFrame(initialBoard, startDepth, -INF, INF));

        var heuristic = 0.0f;
        while (!stack.isEmpty()) {
            var frame = stack.peek();
            var currBoard = frame.board;

            if (!frame.hasChildren()) {
                if (frame.depth == 0 || (frame.depth >= MIN_DEPTH && System.currentTimeMillis() > stopTime)) {
                    heuristic = findHeuristic(currBoard);
                    stack.pop();
                    continue;
                }

                var moves = currBoard.findPotentialMoves();

                // stop when we cannot expand node's children
                if (moves.isEmpty()) {
                    currBoard.skipTurn();
                    moves = currBoard.findPotentialMoves();
                    if (moves.isEmpty()) {
                        heuristic = findHeuristic(currBoard);
                        stack.pop();
                        continue;
                    }
                }

                var hashKey = table.hash(currBoard);

                // check tt table to see if we have a cache hit
                var node = table.get(hashKey);
                if (node != null && node.depth() >= frame.depth) {
                    heuristic = node.heuristic();
                    stack.pop();
                    continue;
                }

                List<OthelloBoard> children = new ArrayList<>();
                for (var move : moves) {
                    var child = currBoard.makeMoved(move);
                    nodesVisited++;
                    children.add(child);
                }

                if (!children.isEmpty()) {
                    frame.children = children;
                    frame.hashKey = hashKey;

                    stack.push(new StackFrame(frame.nextBoard(), frame.depth - 1, frame.alpha, frame.beta));
                } else {
                    if (currBoard.isBlackMove()) {
                        table.put(new TTable.Node(frame.hashKey, frame.alpha, frame.depth));
                        heuristic = frame.alpha;
                        stack.pop();
                    } else {
                        table.put(new TTable.Node(frame.hashKey, frame.beta, frame.depth));
                        heuristic = frame.beta;
                        stack.pop();
                    }
                }
            } else {
                var doPrune = false;

                if (currBoard.isBlackMove()) {
                    frame.alpha = Math.max(frame.alpha, heuristic);
                    if (frame.alpha >= frame.beta) {
                        doPrune = true;
                    }

                    if (frame.hasNext() && !doPrune) {
                        stack.push(new StackFrame(frame.nextBoard(), frame.depth - 1, frame.alpha, frame.beta));
                    } else {
                        table.put(new TTable.Node(frame.hashKey, frame.alpha, frame.depth));
                        heuristic = frame.alpha;
                        stack.pop();
                    }
                } else {
                    frame.beta = Math.min(frame.beta, heuristic);
                    if (frame.beta <= frame.alpha) {
                        doPrune = true;
                    }

                    if (frame.hasNext() && !doPrune) {
                        stack.push(new StackFrame(frame.nextBoard(), frame.depth - 1, frame.alpha, frame.beta));
                    } else {
                        table.put(new TTable.Node(frame.hashKey, frame.beta, frame.depth));
                        heuristic = frame.beta;
                        stack.pop();
                    }
                }
            }
        }

        return heuristic;
    }

    public float evaluate(OthelloBoard board, int depth, float alpha, float beta) {
        // stop early when we reach depth floor, or we've gone over time
        if (depth == 0 || (depth >= MIN_DEPTH && System.currentTimeMillis() > stopTime)) {
            return findHeuristic(board);
        }

        var moves = board.findPotentialMoves();

        // stop when we cannot expand node's children
        if (moves.isEmpty()) {
            board.skipTurn();
            moves = board.findPotentialMoves();
            if (moves.isEmpty()) {
                return findHeuristic(board);
            }
        }

        var hashKey = table.hash(board);

        // check tt table to see if we have a cache hit
        var node = table.get(hashKey);
        if (node != null && node.depth() >= depth) {
            return node.heuristic();
        }

        List<OthelloBoard> children = new ArrayList<>();
        for (var move : moves) {
            var child = board.makeMoved(move);
            nodesVisited++;
            children.add(child);
        }

        // black is the maximizer and white is the minimizer
        if (board.isBlackMove()) {
            for (var child : children) {
                alpha = Math.max(alpha, evaluate(child, depth - 1, alpha, beta));
                if (alpha >= beta) {
                    break;
                }
            }
            table.put(new TTable.Node(hashKey, alpha, depth));
            return alpha;
        } else {
            for (var child : children) {
                beta = Math.min(beta, evaluate(child, depth - 1, alpha, beta));
                if (beta <= alpha) {
                    break;
                }
            }
            table.put(new TTable.Node(hashKey, beta, depth));
            return beta;
        }
    }

    private float findHeuristic(float blackScore, float whiteScore) {
        return (blackScore - whiteScore) / (blackScore + whiteScore);
    }

    public float findHeuristic(OthelloBoard board) {
        return
            50f * findParityHeuristic(board)
                + 100f * findCornerHeuristic(board)
                + 100f * findMobilityHeuristic(board)
                + 50f * findXcHeuristic(board)
                + 100f * findStabilityHeuristic(board);
    }

    private float findParityHeuristic(OthelloBoard board) {
        var whiteScore = 0f;
        var blackScore = 0f;
        for (var row = 0; row < OthelloBoard.getBoardSize(); row++) {
            for (var col = 0; col < OthelloBoard.getBoardSize(); col++) {
                if (board.getSquare(row, col) == OthelloBoard.WHITE) {
                    whiteScore++;
                }
                if (board.getSquare(row, col) == OthelloBoard.BLACK) {
                    blackScore++;
                }
            }
        }
        return findHeuristic(blackScore, whiteScore);
    }

    private float findTilesHeuristic(OthelloBoard board, int[][] tiles) {
        float whiteTiles = 0;
        float blackTiles = 0;
        // iterate over corners and calculate the number of white and black corners
        for (var tile : tiles) {
            var currentColor = board.getSquare(tile[0], tile[1]);
            if (currentColor == OthelloBoard.WHITE) {
                whiteTiles++;
            } else if (currentColor == OthelloBoard.BLACK) {
                blackTiles++;
            }
        }
        if (blackTiles + whiteTiles == 0) {
            return 0f;
        }
        return findHeuristic(blackTiles, whiteTiles);
    }

    private float findCornerHeuristic(OthelloBoard board) {
        return findTilesHeuristic(board, CORNERS);
    }

    private float findXcHeuristic(OthelloBoard board) {
        return findTilesHeuristic(board, XC_SQUARES);
    }

    private float findMobilityHeuristic(OthelloBoard board) {
        float whiteMoves = board.countPotentialMoves(OthelloBoard.WHITE);
        float blackMoves = board.countPotentialMoves(OthelloBoard.BLACK);
        if (whiteMoves + blackMoves == 0) {
            return 0f;
        }
        return findHeuristic(blackMoves, whiteMoves);
    }

    private float findStabilityHeuristic(OthelloBoard board) {
        return 0f;
    }

    public static void main(String[] args) {
        var startTime = System.currentTimeMillis();

        var board = OthelloBoard.initial();
        for (var j = 0; j < 10; j++) {
            var agent = new OthelloAgent();
            var bestMove = agent.findBestMove(board, 8);
            System.out.println(bestMove);
            board = board.makeMoved(bestMove.tile());
        }

        var endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) + " ms");
    }
}
