/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

public record StatsResult(float winnerElo, float loserElo, float winnerEloDiff, float loserEloDiff) {

    public StatsResult() {
        this(0, 0, 0, 0);
    }

    private static String formatElo(float elo) {
        return elo >= 0 ? "+" + elo : Float.toString(elo);
    }

    public String formatWinnerEloDiff() {
        return formatElo(winnerEloDiff);
    }

    public String formatLoserEloDiff() {
        return formatElo(loserEloDiff);
    }

    @Override
    public String toString() {
        return "StatsResult{" +
            "winnerElo=" + winnerElo +
            ", loserElo=" + loserElo +
            ", winnerEloDiff=" + winnerEloDiff +
            ", loserEloDiff=" + loserEloDiff +
            '}';
    }
}
