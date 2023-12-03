/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import java.util.Objects;
import java.util.function.Consumer;

public class EvalRequest<Result> {

    private final Game game;
    private final int depth;
    private final Consumer<Result> onComplete;

    public EvalRequest(Game game, int depth, Consumer<Result> onComplete) {
        this.game = game;
        this.depth = depth;
        this.onComplete = onComplete;
    }

    // no-op eval request
    public EvalRequest(Game game, int depth) {
        this(game, depth, (r) -> {});
    }

    public Game getGame() {
        return game;
    }

    public int getDepth() {
        return depth;
    }

    public void onComplete(Result r) {
        this.onComplete.accept(r);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvalRequest<?> that = (EvalRequest<?>) o;
        return depth == that.depth && Objects.equals(game, that.game);
    }

    @Override
    public int hashCode() {
        return Objects.hash(game, depth);
    }

    @Override
    public String toString() {
        return "EvalRequest{" +
            "board=\n" + game.board() +
            ", depth=" + depth +
            ", onComplete=" + onComplete +
            '}';
    }
}
