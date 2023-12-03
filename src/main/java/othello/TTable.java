/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import javax.annotation.Nullable;

public class TTable {

    private final TTNode[][] cache;
    private int hits = 0;
    private int misses = 0;

    public TTable(int tableSize) {
        // each cache line has 2 elements, one being "replace by depth" and one being "replace always"
        this.cache = new TTNode[tableSize][2];
    }

    public int getHits() {
        return hits;
    }

    public int getMisses() {
        return misses;
    }

    public void put(TTNode node) {
        var h = (int) (node.key() % cache.length);
        // retrieve cache line
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
        // retrieve cache line
        var cacheLine = cache[h];
        // iterate through cache line
        for (var n : cacheLine) {
            // if node is in cache line return it
            if (n != null && n.key() == key) {
                hits++;
                return n;
            }
        }
        misses++;
        return null;
    }
}
