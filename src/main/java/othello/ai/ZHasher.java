package othello.ai;

import othello.board.OthelloBoard;

import java.util.Random;

public class ZHasher
{
    private final int[][] table;

    public ZHasher(int boardSize) {
        Random generator = new Random();
        table = new int[boardSize * boardSize][3];
        for (int i = 0; i < boardSize * boardSize; i++) {
            for (int j = 0; j < 3; j++) {
                table[i][j] = generator.nextInt();
            }
        }
    }

    public int hash(OthelloBoard board) {
        int hash = 0;
        for (int i = 0; i < table.length; i++) {
            hash = hash ^ table[i][board.getSquare(i)];
        }
        return hash;
    }
}
