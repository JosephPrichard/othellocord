package bot.entities;

import javax.persistence.*;

@Entity
@Table(name="Stats", indexes = { @Index(name = "idx_elo", columnList = "elo") })
public class StatsEntity
{
    @Id
    private Long playerId;
    @Column(nullable = false)
    private Float elo;
    @Column(nullable = false)
    private Integer won;
    @Column(nullable = false)
    private Integer lost;
    @Column(nullable = false)
    private Integer drawn;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public float getElo() {
        return elo;
    }

    public void setElo(float elo) {
        this.elo = elo;
    }

    public int getWon() {
        return won;
    }

    public void setWon(int won) {
        this.won = won;
    }

    public int getLost() {
        return lost;
    }

    public void setLost(int lost) {
        this.lost = lost;
    }

    public int getDrawn() {
        return drawn;
    }

    public void setDrawn(int drawn) {
        this.drawn = drawn;
    }

    @Override
    public String toString() {
        return "StatsEntity{" +
            "playerId=" + playerId +
            ", elo=" + elo +
            ", won=" + won +
            ", lost=" + lost +
            ", drawn=" + drawn +
            '}';
    }
}
