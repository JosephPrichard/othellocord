/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import javax.annotation.Nullable;

public class TTable
{
    private final TTNode[][] cache;
    private int hits = 0;
    private int misses = 0;

    public TTable(int tableSize) {
        // Deep2 replacement scheme https://ai.stackexchange.com/questions/11389/what-are-the-common-techniques-one-could-use-to-deal-with-collisions-in-a-transp
        // each cache line has 2 elements, one being "replace by depth" and one being "replace always"
        this.cache = new TTNode[tableSize][2];
    }

    public int getHits() {
        return hits;
    }

    public int getMisses() {
        return misses;
    }

    /**
     * Inserts a new node into the table
     * @param node to be inserted
     */
    public void put(TTNode node) {
        var h = (int) (node.getKey() % cache.length);
        // retrieve cache line
        var cacheLine = cache[h];
        // check if "replace by depth" is populated
        if (cacheLine[0] != null) {
            // populated, new node is better so we do replacement
            if (node.getDepth() > cacheLine[0].getDepth()) {
                cacheLine[1] = cacheLine[0];
                cacheLine[0] = node;
            } else {
                // new node is worse so it should be sent to "replace always"
                cacheLine[1] = node;
            }
        } else {
            // not populated, so it can be used
            cacheLine[0] = node;
        }
    }

    /**
     * Retrieve a node from the table
     * @param key to retrieve for
     * @return ttNode retrieved, null if missing
     */
    @Nullable
    public TTNode get(long key) {
        var h = (int) (key % cache.length);
        // retrieve cache line
        var cacheLine = cache[h];
        // iterate through cache line
        for (var n : cacheLine) {
            // if node is in cache line return it
            if (n != null && n.getKey() == key) {
                hits++;
                return n;
            }
        }
        misses++;
        return null;
    }
}
