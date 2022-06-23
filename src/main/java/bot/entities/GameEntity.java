package bot.entities;

import javax.persistence.*;

@Entity
@IdClass(GamePK.class)
@Table(name="Games")
public class GameEntity
{
    @Id
    private Long whitePlayerId;
    @Id
    private Long blackPlayerId;
    @Column(nullable = false)
    private String board;

    public long getWhitePlayerId() {
        return whitePlayerId;
    }

    public void setWhitePlayerId(long whitePlayerId) {
        this.whitePlayerId = whitePlayerId;
    }

    public long getBlackPlayerId() {
        return blackPlayerId;
    }

    public void setBlackPlayerId(long blackPlayerId) {
        this.blackPlayerId = blackPlayerId;
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    @Override
    public String toString() {
        return "GameEntity{" +
            "whitePlayerId=" + whitePlayerId +
            ", blackPlayerId=" + blackPlayerId +
            ", board='" + board + '\'' +
            '}';
    }
}

