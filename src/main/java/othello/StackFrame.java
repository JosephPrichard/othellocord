/*
 * Copyright (c) Joseph Prichard 2024.
 */

package othello;

import java.util.List;

public class StackFrame {

    private OthelloBoard board;
    private int depth;
    private float alpha;
    private float beta;
    private long hashKey;
    private List<OthelloBoard> children = null;
    private int index = 0;

    public StackFrame(OthelloBoard board, int depth, float alpha, float beta) {
        this.board = board;
        this.depth = depth;
        this.alpha = alpha;
        this.beta = beta;
    }

    public OthelloBoard getBoard() {
        return board;
    }

    public void setBoard(OthelloBoard board) {
        this.board = board;
    }

    public OthelloBoard board() {
        return board;
    }

    public int depth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public float alpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public float beta() {
        return beta;
    }

    public void setBeta(float beta) {
        this.beta = beta;
    }

    public long hashKey() {
        return hashKey;
    }

    public void setHashKey(long hashKey) {
        this.hashKey = hashKey;
    }

    public List<OthelloBoard> children() {
        return children;
    }

    public void setChildren(List<OthelloBoard> children) {
        this.children = children;
    }

    public OthelloBoard nextBoard() {
        var board = children.get(index);
        children.set(index, null);
        index++;
        return board;
    }

    public boolean hasNext() {
        return index < children.size();
    }

    public boolean hasChildren() {
        return children != null;
    }
}
