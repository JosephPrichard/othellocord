/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import java.util.function.Consumer;

public class EvalRequest<Result>
{
    private final Game game;
    private final int depth;
    private final Consumer<Result> onComplete;

    public EvalRequest(Game game, int depth, Consumer<Result> onComplete) {
        this.game = game;
        this.depth = depth;
        this.onComplete = onComplete;
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
    public String toString() {
        return "EvalRequest{" +
            "board=\n" + game.getBoard() +
            ", depth=" + depth +
            ", onComplete=" + onComplete +
            '}';
    }
}
