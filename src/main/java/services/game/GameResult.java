/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import services.player.Player;

import java.util.Objects;

public record GameResult(Player winner, Player loser, boolean isDraw) {

    public static GameResult Draw(Player playerOne, Player playerTwo) {
        return new GameResult(playerOne, playerTwo, true);
    }

    public static GameResult WinLoss(Player winner, Player loser) {
        return new GameResult(winner, loser, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameResult that = (GameResult) o;
        return isDraw == that.isDraw && Objects.equals(winner, that.winner) && Objects.equals(loser, that.loser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(winner, loser, isDraw);
    }

    @Override
    public String toString() {
        return "GameResult{" +
            "winner=" + winner +
            ", loser=" + loser +
            ", isDraw=" + isDraw +
            '}';
    }
}
