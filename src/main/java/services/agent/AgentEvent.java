/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.agent;

import services.game.Game;

import java.util.Objects;
import java.util.function.Consumer;

public record AgentEvent<Result>(Game game, int depth, Consumer<Result> onComplete) {

    public AgentEvent(Game game, int depth) {
        // no-op eval request
        this(game, depth, (r) -> {
        });
    }

    public Game game() {
        return game;
    }

    public int depth() {
        return depth;
    }

    public void applyOnComplete(Result r) {
        this.onComplete.accept(r);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentEvent<?> that = (AgentEvent<?>) o;
        return depth == that.depth && game.equals(that.game);
    }

    @Override
    public int hashCode() {
        return Objects.hash(game, depth);
    }

    @Override
    public String toString() {
        return "AgentEvent{" +
            "game=" + game +
            ", depth=" + depth +
            ", onComplete=" + onComplete +
            '}';
    }
}
