/*
 * Copyright (c) Joseph Prichard 2023.
 */

package engine;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Random;

public class TTable {

    public record Node(long key, float heuristic, int depth) {
    }

    private final int[][] table;
    private final Node[][] cache;
    @Getter
    private int hits = 0;
    @Getter
    private int misses = 0;

    public TTable(int tableSize) {
        // each cache line has 2 elements, one being "replace by depth" and one being "replace always"
        this.cache = new Node[tableSize][2];

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

    public void clear() {
        for (Node[] node : cache) {
            node[0] = null;
            node[1] = null;
        }
    }

    public void put(Node node) {
        int h = (int) (node.key() % cache.length);
        Node[] cacheLine = cache[h];
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
    public Node get(long key) {
        int h = (int) (key % cache.length);
        Node[] cacheLine = cache[h];

        for (Node n : cacheLine) {
            if (n != null && n.key() == key) {
                hits++;
                return n;
            }
        }
        misses++;
        return null;
    }
}
