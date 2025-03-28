/*
 * Copyright (c) Joseph Prichard 2023.
 */

package engine;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EqualsAndHashCode
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
    @Getter
    private boolean blackMove;

    private OthelloBoard(long boardA, long boardB, boolean blackMove) {
        this.boardA = boardA;
        this.boardB = boardB;
        this.blackMove = blackMove;
    }

    public OthelloBoard(boolean blackMove) {
        this.blackMove = blackMove;
    }

    public static OthelloBoard from(OthelloBoard board) {
        return new OthelloBoard(board.boardA, board.boardB, board.blackMove);
    }

    public static OthelloBoard initial() {
        OthelloBoard board = new OthelloBoard(true);
        board.setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2 - 1, WHITE);
        board.setSquare(getBoardSize() / 2, getBoardSize() / 2, WHITE);
        board.setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2, BLACK);
        board.setSquare(getBoardSize() / 2, getBoardSize() / 2 - 1, BLACK);
        return board;
    }

    public static int getBoardSize() {
        return BOARD_SIZE;
    }

    public static boolean inBounds(int row, int col) {
        return row >= 0 && col >= 0 && row < getBoardSize() && col < getBoardSize();
    }

    public float whiteScore() {
        return countDiscs(WHITE);
    }

    public float blackScore() {
        return countDiscs(BLACK);
    }

    public static List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        for (int row = 0; row < getBoardSize(); row++) {
            for (int col = 0; col < getBoardSize(); col++) {
                tiles.add(new Tile(row, col));
            }
        }
        return tiles;
    }

    public int countDiscs(byte color) {
        int discs = 0;
        // iterate through each square and find the discs
        for (Tile tile : TILES) {
            byte c = getSquare(tile);
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

        // check each tile for potential flanks
        for (Tile disc : TILES) {
            if (getSquare(disc) != color) {
                // skip any discs of a different color
                continue;
            }
            // check each direction from tile for potential flank
            for (int[] direction : DIRECTIONS) {
                int row = disc.row() + direction[0];
                int col = disc.col() + direction[1];

                // iterate from tile to next opposite color
                int count = 0;
                while (inBounds(row, col)) {
                    if (getSquare(row, col) != oppositeColor) {
                        break;
                    }
                    row += direction[0];
                    col += direction[1];
                    count++;
                }
                // add move to potential moves list assuming
                // we flank at least once tile, the tile is in bounds and is empty
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

    public OthelloBoard skippedTurn() {
        OthelloBoard copiedBoard = OthelloBoard.from(this);
        copiedBoard.skipTurn();
        return copiedBoard;
    }

    public void skipTurn() {
        blackMove = !blackMove;
    }

    public OthelloBoard makeMoved(String move) {
        return makeMoved(Tile.fromNotation(move));
    }

    public OthelloBoard makeMoved(Tile move) {
        OthelloBoard copiedBoard = OthelloBoard.from(this);
        copiedBoard.makeMove(move);
        return copiedBoard;
    }

    // makes the move on the board, changing the state to a moved state
    // only flips the turn if the next color has moves - otherwise it will be current color turn again
    public void makeMove(Tile move) {
        byte oppositeColor = blackMove ? WHITE : BLACK;
        byte currentColor = blackMove ? BLACK : WHITE;

        setSquare(move.row(), move.col(), currentColor);

        // check each direction of new tile position
        for (int[] direction : DIRECTIONS) {
            int initialRow = move.row() + direction[0];
            int initialCol = move.col() + direction[1];

            int row = initialRow;
            int col = initialCol;

            boolean flank = false;

            // iterate from tile until first potential flank
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

            // flip each tile to opposite color to flank, update tile counts
            while (inBounds(row, col)) {
                if (getSquare(row, col) != oppositeColor) {
                    break;
                }

                setSquare(row, col, currentColor);

                row += direction[0];
                col += direction[1];
            }
        }

        blackMove = !blackMove;
    }

    public void setSquare(int row, int col, byte color) {
        // calculate bit position
        int p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        long clearMask = ~(1L << p) & ~(1L << (p + 1));
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
        int mask = (1 << 2) - 1;
        int p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        return row < HALF_SIZE ? (byte) (mask & (boardA >> p)) : (byte) (mask & (boardB >> p));
    }

    public void setSquare(String square, byte color) {
        Tile tile = Tile.fromNotation(square);
        setSquare(tile, color);
    }

    public void setSquare(Tile tile, byte color) {
        setSquare(tile.row(), tile.col(), color);
    }

    public void setSquare(int position, byte color) {
        setSquare(position / getBoardSize(), position % getBoardSize(), color);
    }

    public byte getSquare(String square) {
        Tile tile = Tile.fromNotation(square);
        return getSquare(tile);
    }

    public byte getSquare(int position) {
        return getSquare(position / getBoardSize(), position % getBoardSize());
    }

    public byte getSquare(Tile tile) {
        return getSquare(tile.row(), tile.col());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("  ");
        for (int i = 0; i < getBoardSize(); i++) {
            builder.append((char) ('a' + (char) i));
            builder.append(" ");
        }
        builder.append("\n");
        for (int row = 0; row < getBoardSize(); row++) {
            builder.append(row + 1);
            builder.append(" ");
            for (int col = 0; col < getBoardSize(); col++) {
                builder.append(getSquare(row, col));
                builder.append(" ");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        OthelloBoard board = OthelloBoard.initial();
        for (int j = 0; j < 10; j++) {
            List<Tile> moves = board.findPotentialMoves();
            for (Tile move : moves) {
                System.out.print(move + " ");
            }
            System.out.println();
            board.makeMove(moves.get(0));
        }
    }
}
