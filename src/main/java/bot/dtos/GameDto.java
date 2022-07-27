package bot.dtos;

import othello.board.OthelloBoard;

public class GameDto
{
    private OthelloBoard board;
    private PlayerDto whitePlayer;
    private PlayerDto blackPlayer;

    public void setBoard(OthelloBoard board) {
        this.board = board;
    }

    public OthelloBoard getBoard() {
        return board;
    }

    public PlayerDto getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(PlayerDto whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public PlayerDto getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(PlayerDto blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public PlayerDto getCurrentPlayer() {
        return board.isBlackMove() ? blackPlayer : whitePlayer;
    }

    public PlayerDto getOtherPlayer() {
        return board.isBlackMove() ? whitePlayer : blackPlayer;
    }

    public int getBlackScore() {
        return (int) board.blackScore();
    }

    public int getWhiteScore() {
        return (int) board.whiteScore();
    }

    public boolean isAgainstBot() {
        return whitePlayer.isBot() || blackPlayer.isBot();
    }

    public boolean isGameOver() {
        return board.isGameOver();
    }

    public GameResultDto getResult() {
        float diff = board.blackScore() - board.whiteScore();
        if (diff > 0) {
            return new GameResultDto(blackPlayer, whitePlayer);
        } else if (diff < 0) {
            return new GameResultDto(whitePlayer, blackPlayer);
        } else {
            return GameResultDto.Draw();
        }
    }

    public GameResultDto getForfeitResult() {
        return new GameResultDto(getOtherPlayer(), getCurrentPlayer());
    }
}
