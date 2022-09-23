package modules.agent;

import modules.game.Game;
import othello.board.OthelloBoard;

import java.util.function.Consumer;

public class AgentRequest<Result>
{
    private final Game game;
    private final int depth;
    private final Consumer<Result> onComplete;

    public AgentRequest(Game game, int depth, Consumer<Result> onComplete) {
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

    public Consumer<Result> getOnComplete() {
        return onComplete;
    }

    @Override
    public String toString() {
        return "AgentRequest{" +
            "board=\n" + game.getBoard() +
            ", depth=" + depth +
            ", onComplete=" + onComplete +
            '}';
    }
}
