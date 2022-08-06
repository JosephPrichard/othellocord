package othello.board;

import java.util.List;

public interface OthelloBoard
{
    int BOARD_SIZE = 8;
    int HALF_SIZE = BOARD_SIZE / 2;

    byte EMPTY = 0;
    byte WHITE = 1;
    byte BLACK = 2;

    int[][] DIRECTIONS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    int[][] CORNERS = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
    int[][] XC_SQUARES = {{1, 1}, {1, 6}, {6, 1}, {6, 6}, {0, 1}, {0, 6}, {7, 1}, {7, 6}, {1, 0}, {1, 7}, {6, 0}, {6, 7}};

    static int getBoardSize() {
        return OthelloBitBoard.BOARD_SIZE;
    }

    static boolean inBounds(int row, int col) {
        return row >= 0 && col >= 0 && row < getBoardSize() && col < getBoardSize();
    }

    boolean isBlackMove();

    float whiteScore();

    float blackScore();

    OthelloBoard copy();

    List<Tile> tiles();

    float heuristic();

    List<Tile> findPotentialMoves();

    boolean isGameOver();

    void makeMove(Tile move);

    void setSquare(String square, byte color);

    void setSquare(int position, byte color);

    byte getSquare(String square);

    byte getSquare(int position);

    byte getSquare(Tile tile);
}
