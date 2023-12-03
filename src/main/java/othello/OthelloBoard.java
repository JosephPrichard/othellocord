/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OthelloBoard {

    private static final int BOARD_SIZE = 8;
    private static final int HALF_SIZE = BOARD_SIZE / 2;
    public static final byte EMPTY = 0;
    public static final byte WHITE = 1;
    public static final byte BLACK = 2;
    private static final int[][] DIRECTIONS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final List<Tile> TILES = tiles();

    private long boardA;
    private long boardB;
    private boolean blackMove;

    public OthelloBoard() {
        setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2 - 1, WHITE);
        setSquare(getBoardSize() / 2, getBoardSize() / 2, WHITE);
        setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2, BLACK);
        setSquare(getBoardSize() / 2, getBoardSize() / 2 - 1, BLACK);
        blackMove = true;
    }

    public OthelloBoard(boolean blackMove) {
        this.blackMove = blackMove;
    }

    public OthelloBoard(OthelloBoard othelloBoard) {
        this.boardA = othelloBoard.boardA;
        this.boardB = othelloBoard.boardB;
        this.blackMove = othelloBoard.blackMove;
    }

    public static int getBoardSize() {
        return BOARD_SIZE;
    }

    public static boolean inBounds(int row, int col) {
        return row >= 0 && col >= 0 && row < getBoardSize() && col < getBoardSize();
    }

    public boolean isBlackMove() {
        return blackMove;
    }

    public float whiteScore() {
        return countDiscs(WHITE);
    }

    public float blackScore() {
        return countDiscs(BLACK);
    }

    public OthelloBoard copy() {
        return new OthelloBoard(this);
    }

    public static List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        for (var row = 0; row < getBoardSize(); row++) {
            for (var col = 0; col < getBoardSize(); col++) {
                tiles.add(new Tile(row, col));
            }
        }
        return tiles;
    }

    public int countDiscs(byte color) {
        var discs = 0;
        // iterate through each square and find the discs
        for (var tile : TILES) {
            var c = getSquare(tile);
            if (c == color) {
                discs++;
            }
        }
        return discs;
    }

    public List<Tile> findPotentialMoves() {
        List<Tile> moves = new ArrayList<>();
        onPotentialMoves(moves::add);
        return moves;
    }

    public void onPotentialMoves(Consumer<Tile> onMove) {
        onPotentialMoves(blackMove ? BLACK : WHITE, onMove);
    }

    public void onPotentialMoves(byte color, Consumer<Tile> onMove) {
        int oppositeColor = color == BLACK ? WHITE : BLACK;

        // check each disc for potential flanks
        for (var disc : TILES) {
            if (getSquare(disc) != color) {
                // skip any discs of a different color
                continue;
            }
            // check each direction from disc for potential flank
            for (var direction : DIRECTIONS) {
                var row = disc.getRow() + direction[0];
                var col = disc.getCol() + direction[1];

                // iterate from disc to next opposite color
                var count = 0;
                while (inBounds(row, col)) {
                    if (getSquare(row, col) != oppositeColor) {
                        break;
                    }
                    row += direction[0];
                    col += direction[1];
                    count++;
                }
                // add move to potential moves list assuming
                // we flank at least once disc, the tile is in bounds and is empty
                if (count > 0 && inBounds(row, col) && getSquare(row, col) == EMPTY) {
                    onMove.accept(new Tile(row, col));
                }
            }
        }

    }

    public int countPotentialMoves(byte color) {
        int[] count = {0};
        onPotentialMoves(color, (tile) -> ++count[0]);
        return count[0];
    }

    public boolean isGameOver() {
        return countPotentialMoves(blackMove ? BLACK : WHITE) <= 0;
    }

    public void makeMove(Tile move) {
        var oppositeColor = blackMove ? WHITE : BLACK;
        var currentColor = blackMove ? BLACK : WHITE;

        blackMove = !blackMove;
        setSquare(move.getRow(), move.getCol(), currentColor);

        // check each direction of new disc position
        for (var direction : DIRECTIONS) {
            var initialRow = move.getRow() + direction[0];
            var initialCol = move.getCol() + direction[1];

            var row = initialRow;
            var col = initialCol;

            var flank = false;

            // iterate from disc until first potential flank
            while (inBounds(row, col)) {
                if (getSquare(row, col) == currentColor) {
                    flank = true;
                    break;
                } else if (getSquare(row, col) == EMPTY) {
                    break;
                }
                row += direction[0];
                col += direction[1];
            }

            if (!flank) {
                continue;
            }

            row = initialRow;
            col = initialCol;

            // flip each disc to opposite color to flank, update disc counts
            while (inBounds(row, col)) {
                if (getSquare(row, col) != oppositeColor) {
                    break;
                }

                setSquare(row, col, currentColor);

                row += direction[0];
                col += direction[1];
            }
        }
    }

    public void setSquare(int row, int col, byte color) {
        // calculate bit position
        var p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        var clearMask = ~(1L << p) & ~(1L << (p + 1));
        // clear bits then set bits
        if (row < HALF_SIZE) {
            boardA &= clearMask;
            boardA |= (long) color << p;
        } else {
            boardB &= clearMask;
            boardB |= (long) color << p;
        }
    }

    public byte getSquare(int row, int col) {
        var mask = (1 << 2) - 1;
        var p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        return row < HALF_SIZE ? (byte) (mask & (boardA >> p)) : (byte) (mask & (boardB >> p));
    }

    public void setSquare(String square, byte color) {
        var tile = new Tile(square);
        setSquare(tile, color);
    }

    public void setSquare(Tile tile, byte color) {
        setSquare(tile.getRow(), tile.getCol(), color);
    }

    public void setSquare(int position, byte color) {
        setSquare(position / getBoardSize(), position % getBoardSize(), color);
    }

    public byte getSquare(String square) {
        var tile = new Tile(square);
        return getSquare(tile);
    }

    public byte getSquare(int position) {
        return getSquare(position / getBoardSize(), position % getBoardSize());
    }

    public byte getSquare(Tile tile) {
        return getSquare(tile.getRow(), tile.getCol());
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        // add space for better board indentation
        builder.append("  ");
        // add each column header as letter
        for (var i = 0; i < getBoardSize(); i++) {
            builder.append((char) ('a' + (char) i));
            builder.append(" ");
        }
        builder.append("\n");
        // add each matrix element in board with row header
        for (var row = 0; row < getBoardSize(); row++) {
            builder.append(row + 1);
            builder.append(" ");
            for (var col = 0; col < getBoardSize(); col++) {
                builder.append(getSquare(row, col));
                builder.append(" ");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        var board = new OthelloBoard();
        for (var j = 0; j < 10; j++) {
            var moves = board.findPotentialMoves();
            for (var move : moves) {
                System.out.print(move + " ");
            }
            System.out.println();
            board.makeMove(board.findPotentialMoves().get(0));
        }
    }
}
