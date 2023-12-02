/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import java.util.Random;

public class ZHasher {
    private final int[][] table;

    public ZHasher() {
        var tableLen = OthelloBoard.getBoardSize() * OthelloBoard.getBoardSize();
        var generator = new Random();
        table = new int[tableLen][3];
        for (var i = 0; i < tableLen; i++) {
            for (var j = 0; j < 3; j++) {
                var n = generator.nextInt();
                table[i][j] = n >= 0 ? n : -n;
            }
        }
    }

    public long hash(OthelloBoard board) {
        long hash = 0;
        for (var i = 0; i < table.length; i++) {
            hash = hash ^ table[i][board.getSquare(i)];
        }
        return hash;
    }
}
