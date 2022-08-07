package modules.game;

import modules.player.Player;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class GameResult
{
    private final Pair<Player, Player> playerPair;
    private Pair<Float, Float> eloPair;
    private Pair<Float, Float> eloDiffPair;

    public static GameResult Draw() {
        return new GameResult();
    }

    public GameResult() {
        this.playerPair = null;
    }

    public GameResult(Player winner, Player loser) {
        this.playerPair =  new ImmutablePair<>(winner, loser);
    }

    public boolean isDraw() {
        return playerPair == null;
    }

    public Player getWinner() {
        return playerPair != null ? playerPair.getLeft() : null;
    }

    public Player getLoser() {
        return playerPair != null ? playerPair.getRight() : null;
    }

    public void setElo(float eloWinner, float eloLoser) {
        this.eloPair = new ImmutablePair<>(eloWinner, eloLoser);
    }

    public float getWinnerElo() {
        return eloPair.getLeft();
    }

    public float getLoserElo() {
        return eloPair.getRight();
    }

    public void setEloDiff(float diffWinner, float diffLoser) {
        this.eloDiffPair = new ImmutablePair<>(diffWinner, diffLoser);
    }

    private static String formatElo(float elo) {
        return elo >= 0 ? "+" + elo : Float.toString(elo);
    }

    public String formatWinnerDiffElo() {
        return formatElo(eloDiffPair.getLeft());
    }

    public String formatLoserDiffElo() {
        return formatElo(eloDiffPair.getRight());
    }

    @Override
    public String toString() {
        return "GameResult{" +
            "playerPair=" + playerPair +
            ", eloPair=" + eloPair +
            ", eloDiffPair=" + eloDiffPair +
            '}';
    }
}
