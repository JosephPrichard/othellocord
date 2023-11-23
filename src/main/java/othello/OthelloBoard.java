/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import java.util.ArrayList;
import java.util.List;

public class OthelloBoard
{
    public static final int BOARD_SIZE = 8;
    public static final int HALF_SIZE = BOARD_SIZE / 2;

    public static final byte EMPTY = 0;
    public static final byte WHITE = 1;
    public static final byte BLACK = 2;

    public static final int[][] DIRECTIONS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

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
        return findDiscs(WHITE).size();
    }

    public float blackScore() {
        return findDiscs(BLACK).size();
    }

    public OthelloBoard copy() {
        return new OthelloBoard(this);
    }

    public List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        for (int row = 0; row < getBoardSize(); row++) {
            for (int col = 0; col < getBoardSize(); col++) {
                tiles.add(new Tile(row, col));
            }
        }
        return tiles;
    }

    /**
     * Counts all discs on the board
     * @return the number of discs on the board
     */
    public int countDiscs() {
        int discs = 0;

        // iterate through each square and find the discs
        for (int row = 0; row < getBoardSize(); row++) {
            for (int col = 0; col < getBoardSize(); col++) {
                if (getSquare(row, col) != EMPTY)
                    discs += 0;
            }
        }
        return discs;
    }

    /**
     * Searches the board for all othello discs of matching color
     * @param color to find discs for
     * @return list containing positions of the discs
     */
    public List<Tile> findDiscs(byte color) {
        List<Tile> discs = new ArrayList<>();

        // iterate through each square and find the discs
        for (int row = 0; row < getBoardSize(); row++) {
            for (int col = 0; col < getBoardSize(); col++) {
                if (getSquare(row, col) == color)
                    discs.add(new Tile(row, col));
            }
        }
        return discs;
    }

    /**
     * Find the available moves the othello board depending on the board state
     * @return a list containing the moves
     */
    public List<Tile> findPotentialMoves() {
        return findPotentialMoves(blackMove ? BLACK : WHITE);
    }

    /**
     * Find the available moves the othello board depending on the board state
     * @return a list containing the moves
     */
    public List<Tile> findPotentialMoves(byte color) {
        List<Tile> moves = new ArrayList<>();

        List<Tile> discs = findDiscs(color);
        int oppositeColor = color == BLACK ? WHITE : BLACK;

        // check each disc for potential flanks
        for (Tile disc : discs) {
            // check each direction from disc for potential flank
            for (int[] direction : DIRECTIONS) {
                int row = disc.getRow() + direction[0];
                int col = disc.getCol() + direction[1];

                // iterate from disc to next opposite color
                int count = 0;
                while (inBounds(row, col)) {
                    if (getSquare(row, col) != oppositeColor)
                        break;
                    row += direction[0];
                    col += direction[1];
                    count++;
                }
                // add move to potential moves list assuming
                // we flank at least once disc, the tile is in bounds and is empty
                if (count > 0 && inBounds(row, col) && getSquare(row, col) == EMPTY) {
                    moves.add(new Tile(row, col));
                }
            }
        }

        return moves;
    }

    /**
     * Find the available moves the othello board depending on the board state
     * @return a list containing the moves
     */
    public int countPotentialMoves(byte color) {
        return findPotentialMoves(color).size();
    }

    public boolean isGameOver() {
        return countPotentialMoves(blackMove ? BLACK : WHITE) <= 0;
    }

    /**
     * Makes a move on a position on the othello board
     * @param move to make move
     */
    public void makeMove(Tile move) {
        byte oppositeColor = blackMove ? WHITE : BLACK;
        byte currentColor = blackMove ? BLACK : WHITE;

        blackMove = !blackMove;
        setSquare(move.getRow(), move.getCol(), currentColor);

        // check each direction of new disc position
        for (int[] direction : DIRECTIONS) {
            int initialRow = move.getRow() + direction[0];
            int initialCol = move.getCol() + direction[1];

            int row = initialRow;
            int col = initialCol;

            boolean flank = false;

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
                if (getSquare(row, col) != oppositeColor)
                    break;

                setSquare(row, col, currentColor);

                row += direction[0];
                col += direction[1];
            }
        }
    }

    public void setSquare(int row, int col, byte color) {
        // calculate bit position
        int p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        long clearMask = ~(1L << p) & ~(1L << (p + 1));
        // clear bits then set bits
        if (row < HALF_SIZE ) {
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

    /**
     * Sets a square to a value on the board by othello board notation
     * @param square in othello board notation
     * @param color to set, must be one of the constants
     */
    public void setSquare(String square, byte color) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        setSquare(row, col, color);
    }

    /**
     * Gets a square from the othello board
     * @param position on the board
     */
    public void setSquare(int position, byte color) {
        setSquare(position / getBoardSize(), position % getBoardSize(), color);
    }

    /**
     * Gets a square from the othello board
     * @param square in othello board notation
     */
    public byte getSquare(String square) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        return getSquare(row, col);
    }

    /**
     * Gets a square from the othello board
     * @param position on the board
     */
    public byte getSquare(int position) {
        return getSquare(position / getBoardSize(), position % getBoardSize());
    }

    /**
     * Gets a square from the othello board
     * @param tile to get for
     */
    public byte getSquare(Tile tile) {
        return getSquare(tile.getRow(), tile.getCol());
    }

    /**
     * Converts the internal board to a parsable, readable string
     * @return internal board as a string
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        // add space for better board indentation
        builder.append("  ");
        // add each column header as letter
        for (int i = 0; i < getBoardSize(); i++) {
            builder.append((char)('a' + (char)i));
            builder.append(" ");
        }
        builder.append("\n");
        // add each matrix element in board with row header
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
        OthelloBoard board = new OthelloBoard();
        for (int j = 0; j < 10; j++) {
            var moves = board.findPotentialMoves();
            for (Tile move : moves) {
                System.out.print(move + " ");
            }
            System.out.println();
            board.makeMove(board.findPotentialMoves().get(0));
        }
    }
}
