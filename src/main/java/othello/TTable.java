/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import javax.annotation.Nullable;
import java.util.Random;

public class TTable {

    private final int[][] table;
    private final TTNode[][] cache;
    private int hits = 0;
    private int misses = 0;

    public TTable(int tableSize) {
        // each cache line has 2 elements, one being "replace by depth" and one being "replace always"
        this.cache = new TTNode[tableSize][2];

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

    public int getHits() {
        return hits;
    }

    public int getMisses() {
        return misses;
    }

    public long hash(OthelloBoard board) {
        long hash = 0;
        for (var i = 0; i < table.length; i++) {
            hash = hash ^ table[i][board.getSquare(i)];
        }
        return hash;
    }

    public void clear() {
        for (var node : cache) {
            node[0] = null;
            node[1] = null;
        }
    }

    public void put(TTNode node) {
        var h = (int) (node.key() % cache.length);
        var cacheLine = cache[h];
        // check if "replace by depth" is populated
        if (cacheLine[0] != null) {
            // populated, new node is better so we do replacement
            if (node.depth() > cacheLine[0].depth()) {
                cacheLine[1] = cacheLine[0];
                cacheLine[0] = node;
            } else {
                // new node is worse, so it should be sent to "replace always"
                cacheLine[1] = node;
            }
        } else {
            // not populated, so it can be used
            cacheLine[0] = node;
        }
    }

    @Nullable
    public TTNode get(long key) {
        var h = (int) (key % cache.length);
        var cacheLine = cache[h];

        for (var n : cacheLine) {
            if (n != null && n.key() == key) {
                hits++;
                return n;
            }
        }
        misses++;
        return null;
    }
}
