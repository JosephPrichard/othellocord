package bot.dtos;

import othello.board.OthelloBoard;

import java.util.function.Consumer;

public class AiRequestDto<Result>
{
    private final OthelloBoard board;
    private final int depth;
    private final Consumer<Result> onComplete;

    public AiRequestDto(OthelloBoard board, int depth, Consumer<Result> onComplete) {
        this.board = board;
        this.depth = depth;
        this.onComplete = onComplete;
    }

    public OthelloBoard getBoard() {
        return board;
    }

    public int getDepth() {
        return depth;
    }

    public Consumer<Result> getOnComplete() {
        return onComplete;
    }
}
