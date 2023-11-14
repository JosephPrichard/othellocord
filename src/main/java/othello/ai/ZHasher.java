/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello.ai;

import othello.board.OthelloBoard;

import java.util.Random;

public class ZHasher
{
    private final int[][] table;

    public ZHasher() {
        int tableLen = OthelloBoard.getBoardSize() * OthelloBoard.getBoardSize();
        Random generator = new Random();
        table = new int[tableLen][3];
        for (int i = 0; i < tableLen; i++) {
            for (int j = 0; j < 3; j++) {
                int n = generator.nextInt();
                table[i][j] = n >= 0 ? n : -n;
            }
        }
    }

    public long hash(OthelloBoard board) {
        long hash = 0;
        for (int i = 0; i < table.length; i++) {
            hash = hash ^ table[i][board.getSquare(i)];
        }
        return hash;
    }
}
