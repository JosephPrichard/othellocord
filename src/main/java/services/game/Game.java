/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import othello.OthelloBoard;
import services.player.Player;

import java.util.Objects;

public class Game {

    private final OthelloBoard board;
    private final Player whitePlayer;
    private final Player blackPlayer;

    public Game(OthelloBoard board, Player whitePlayer, Player blackPlayer) {
        this.board = board;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    public Game(Player whitePlayer, Player blackPlayer) {
        this.board = new OthelloBoard();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    public OthelloBoard board() {
        return board;
    }

    public Player whitePlayer() {
        return whitePlayer;
    }

    public Player blackPlayer() {
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
            return GameResult.WinLoss(blackPlayer, whitePlayer);
        } else if (diff < 0) {
            return GameResult.WinLoss(whitePlayer, blackPlayer);
        } else {
            return GameResult.Draw(whitePlayer, blackPlayer);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Game game = (Game) o;
        return game.whitePlayer.equals(whitePlayer) && game.blackPlayer.equals(blackPlayer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(whitePlayer, blackPlayer);
    }

    public GameResult getForfeitResult() {
        return GameResult.WinLoss(getOtherPlayer(), getCurrentPlayer());
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
