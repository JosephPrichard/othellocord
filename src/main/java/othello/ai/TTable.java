package othello.ai;

import java.util.*;

public class TTable
{
    private final List<Deque<TTNode>> cache;
    private final int clSize;

    public TTable(int tableSize, int clSize) {
        this.cache = new ArrayList<>(tableSize);
        this.clSize = clSize;
    }

    /**
     * Inserts a new node into the table
     * @param node to be inserted
     */
    public void put(TTNode node) {
        int h = node.getKey() % cache.size();
        // retrieve cache line
        Deque<TTNode> cacheLine = cache.get(h);
        // check if cache line is populated
        if (cacheLine != null) {
            // add node to cache line
            cacheLine.addFirst(node);
            if (cacheLine.size() >= clSize) {
                // remove least recently used from cache line if cache line exceeds size
                cacheLine.pollLast();
            }
        } else {
            // if not, create a new cache line with node and add it to table
            cacheLine = new LinkedList<>();
            cacheLine.add(node);
            cache.set(h, cacheLine);
        }
    }

    /**
     * Retrieve a node from the table
     * @param key to retrieve for
     * @return ttNode retrieved
     */
    public TTNode get(int key) {
        int h = key % cache.size();
        // retrieve cache line
        Deque<TTNode> cacheLine = cache.get(h);
        // check if cache line is populated
        if (cacheLine != null) {
            // iterate through cache line
            Iterator<TTNode> it = cacheLine.iterator();
            while(it.hasNext()) {
                TTNode n = it.next();
                // if node is in cache line, move it to front and return it
                if (n.getKey() == key) {
                    it.remove();
                    cacheLine.addFirst(n);
                    return n;
                }
            }
            return null;
        }
        return null;
    }
}
