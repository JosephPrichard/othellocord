package othello.ai;

public class TTNode
{
    private int key;
    private int heuristic;
    private int depth;

    public TTNode(int key, int heuristic, int depth) {
        this.key = key;
        this.heuristic = heuristic;
        this.depth = depth;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public int getHeuristic() {
        return heuristic;
    }

    public void setHeuristic(int heuristic) {
        this.heuristic = heuristic;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
