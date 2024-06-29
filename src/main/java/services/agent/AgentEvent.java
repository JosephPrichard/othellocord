/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.agent;

import othello.OthelloBoard;

import java.util.Objects;
import java.util.function.Consumer;

public record AgentEvent<Result>(OthelloBoard board, int depth, Consumer<Result> onComplete) {

    public AgentEvent(OthelloBoard board, int depth) {
        // no-op eval request
        this(board, depth, (r) -> {
        });
    }

    public void applyOnComplete(Result r) {
        this.onComplete.accept(r);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentEvent<?> that = (AgentEvent<?>) o;
        return depth == that.depth && board.equals(that.board);
    }

    @Override
    public int hashCode() {
        return Objects.hash(board, depth);
    }

    @Override
    public String toString() {
        return "AgentEvent{" +
            "board=" + board +
            ", depth=" + depth +
            ", onComplete=" + onComplete +
            '}';
    }
}
