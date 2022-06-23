package bot.entities;

import java.io.Serializable;
import java.util.Objects;

public class GamePK implements Serializable
{
    private Long whitePlayerId;
    private Long blackPlayerId;

    public Long getWhitePlayerId() {
        return whitePlayerId;
    }

    public void setWhitePlayerId(Long whitePlayerId) {
        this.whitePlayerId = whitePlayerId;
    }

    public Long getBlackPlayerId() {
        return blackPlayerId;
    }

    public void setBlackPlayerId(Long blackPlayerId) {
        this.blackPlayerId = blackPlayerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GamePK gamePK = (GamePK) o;
        return Objects.equals(whitePlayerId, gamePK.whitePlayerId) && Objects.equals(blackPlayerId, gamePK.blackPlayerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(whitePlayerId, blackPlayerId);
    }

    @Override
    public String toString() {
        return "GamePK{" +
            "whitePlayerId=" + whitePlayerId +
            ", blackPlayerId=" + blackPlayerId +
            '}';
    }
}
