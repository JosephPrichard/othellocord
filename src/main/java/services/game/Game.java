/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import othello.OthelloBoard;

public class Game
{
    private final OthelloBoard board;
    private final Player whitePlayer;
    private final Player blackPlayer;

    public Game(OthelloBoard board, Player whitePlayer, Player blackPlayer) {
        this.board = board;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    public OthelloBoard getBoard() {
        return board;
    }

    public Player getWhitePlayer() {
        return whitePlayer;
    }

    public Player getBlackPlayer() {
        return blackPlayer;
    }

    public Player getCurrentPlayer() {
        return board.isBlackMove() ? blackPlayer : whitePlayer;
    }

    public Player getOtherPlayer() {
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

    public GameResult getResult() {
        var diff = board.blackScore() - board.whiteScore();
        if (diff > 0) {
            return new GameResult(blackPlayer, whitePlayer);
        } else if (diff < 0) {
            return new GameResult(whitePlayer, blackPlayer);
        } else {
            return GameResult.Draw();
        }
    }

    public GameResult getForfeitResult() {
        return new GameResult(getOtherPlayer(), getCurrentPlayer());
    }

    @Override
    public String toString() {
        return "Game{" +
            "board=\n" + board +
            ", whitePlayer=" + whitePlayer +
            ", blackPlayer=" + blackPlayer +
            '}';
    }
}
