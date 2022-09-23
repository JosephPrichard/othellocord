package othello.ai;

public class TTNode
{
    private final long key;
    private final float heuristic;
    private final int depth;

    public TTNode(long key, float heuristic, int depth) {
        this.key = key;
        this.heuristic = heuristic;
        this.depth = depth;
    }

    public long getKey() {
        return key;
    }

    public float getHeuristic() {
        return heuristic;
    }

    public int getDepth() {
        return depth;
    }
}
