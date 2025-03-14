/*
 * Copyright (c) Joseph Prichard 2024.
 */

package models;

import engine.OthelloBoard;
import engine.Tile;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@EqualsAndHashCode
public class Game {

    private Player whitePlayer;
    private Player blackPlayer;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private OthelloBoard board;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<Tile> currPotentialMoves;

    public Game(OthelloBoard board, Player blackPlayer, Player whitePlayer) {
        this.board = board;
        this.blackPlayer = blackPlayer;
        this.whitePlayer = whitePlayer;
        this.currPotentialMoves = null;
    }

    public static Game start(Player blackPlayer, Player whitePlayer) {
        return new Game(OthelloBoard.initial(), blackPlayer, whitePlayer);
    }

    public static Game from(Game game) {
        Game copiedGame = new Game(OthelloBoard.from(game.board), game.blackPlayer, game.whitePlayer);
        copiedGame.currPotentialMoves = game.currPotentialMoves;
        return copiedGame;
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

    public List<Tile> findPotentialMoves() {
        if (currPotentialMoves != null) {
            return currPotentialMoves;
        }
        List<Tile> potentialMoves = board.findPotentialMoves();
        currPotentialMoves = potentialMoves;
        return potentialMoves;
    }

    public void makeMove(Tile move) {
        board.makeMove(move);
        currPotentialMoves = null;

        if (findPotentialMoves().isEmpty()) {
            board.skipTurn();
            currPotentialMoves = null;
        }
    }

    public boolean isOver() {
        return findPotentialMoves().isEmpty();
    }

    public boolean isBlackMove() {
        return board.isBlackMove();
    }

    public Result createResult() {
        int diff = getBlackScore() - getWhiteScore();
        if (diff > 0) {
            return Result.WinLoss(blackPlayer, whitePlayer);
        } else if (diff < 0) {
            return Result.WinLoss(whitePlayer, blackPlayer);
        } else {
            return Result.Draw(whitePlayer, blackPlayer);
        }
    }

    public Result createForfeitResult(Player forfeitingPlayer) {
        if (whitePlayer.equals(forfeitingPlayer)) {
            return Result.WinLoss(blackPlayer, whitePlayer);
        } else if (blackPlayer.equals(forfeitingPlayer)) {
            return Result.WinLoss(whitePlayer, blackPlayer);
        } else {
            throw new IllegalStateException("Player not part of a game attempted to forfeit");
        }
    }

    public record Result(Player winner, Player loser, boolean isDraw) {

        public static Result Draw(Player playerOne, Player playerTwo) {
            return new Result(playerOne, playerTwo, true);
        }

        public static Result WinLoss(Player winner, Player loser) {
            return new Result(winner, loser, false);
        }
    }
}
