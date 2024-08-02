/*
 * Copyright (c) Joseph Prichard 2024.
 */

package models;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

@Entity
@Table(name = "Stats", indexes = {@Index(name = "idx_elo", columnList = "elo")})
@ToString
@EqualsAndHashCode
public class StatsEntity {

    @Id
    public Long playerId;
    @Column(nullable = false)
    public Float elo;
    @Column(nullable = false)
    public Integer won;
    @Column(nullable = false)
    public Integer lost;
    @Column(nullable = false)
    public Integer drawn;

    public StatsEntity() {
    }

    public StatsEntity(Long playerId) {
        this(playerId, 0f, 0, 0, 0);
    }

    public StatsEntity(Long playerId, Float elo, Integer won, Integer lost, Integer drawn) {
        this.playerId = playerId;
        this.elo = elo;
        this.won = won;
        this.lost = lost;
        this.drawn = drawn;
    }
}
