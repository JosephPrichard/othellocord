package bot.dtos;

import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class GameResultDto
{
    private final Pair<PlayerDto, PlayerDto> playerPair;
    private Pair<Float, Float> eloPair;
    private Pair<Float, Float> eloDiffPair;

    public static GameResultDto Draw() {
        return new GameResultDto();
    }

    public GameResultDto() {
        this.playerPair = null;
    }

    public GameResultDto(PlayerDto winner, PlayerDto loser) {
        this.playerPair =  new ImmutablePair<>(winner, loser);
    }

    public boolean isDraw() {
        return playerPair == null;
    }

    public PlayerDto getWinner() {
        return playerPair != null ? playerPair.getLeft() : null;
    }

    public PlayerDto getLoser() {
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

    public float getWinnerDiffElo() {
        return eloDiffPair.getLeft();
    }

    public float getLoserDiffElo() {
        return eloDiffPair.getRight();
    }

    public String formatWinnerDiffElo() {
        return Float.toString(eloDiffPair.getLeft());
    }

    public String formatLoserDiffElo() {
        return Float.toString(eloDiffPair.getRight());
    }
}
